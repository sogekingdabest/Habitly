package com.monsteraltech.habitly.feature.aiassistant.domain.util

/**
 * JSON Schemas the extraction turn is constrained to.
 *
 * From LiteRT-LM 0.19.0 the engine can mask the logits at every decoding step against a schema, so
 * the model **cannot** emit anything the schema does not allow: no prose wrapped around the JSON,
 * no ```json fence, no invented key, no value outside an enum and no string where an integer
 * belongs. It is a guarantee rather than a request, which is what the prompt alone could never be.
 *
 * Each schema mirrors its system prompt in `AiAssistantRepositoryImpl`, so the two change
 * together; `AiExtractionSchemasTest` fails the build if a schema stops being valid JSON, stops
 * agreeing with what the parsers accept, or drifts from the vocabulary the app itself offers.
 *
 * What the schemas deliberately do **not** constrain is as important as what they do. A value can
 * be well-formed and still be wrong, and these models are small: the parsers stay exactly as
 * tolerant as they were. They also read the `@@RUTINA@@` and `@@LISTA@@` blocks the model appends
 * to an ordinary chat answer (see [AiStructuredBlocks]), where there is no schema in force at all.
 */
object AiExtractionSchemas {

    /**
     * Routine extraction.
     *
     * `additionalProperties: false` carries as much weight as the rest: without it the model may
     * bolt extra keys onto each object, which was one of the ways the answer used to drift.
     *
     * `maxItems` matches the cap `ParseAiRoutinesUseCase` already applies (it discards anything
     * past ten), so constraining here changes no behaviour and stops a runaway list from eating
     * the token budget.
     *
     * **`days` and `interval_days` are here so the frequency enum can be honoured.** Until they
     * were, two of its three values could not be: a `semanal` routine arrived with no days and
     * `ParseAiRoutinesUseCase` rightly degraded it to daily, because a weekly routine with no days
     * would never come due; a `cada_n_dias` routine arrived with no interval and silently became
     * every three days, the parser's default. The parser has read both fields all along — the
     * schema was what forbade them, since `additionalProperties: false` leaves no room for a key
     * the schema does not name.
     *
     * Both are **optional**, and that is the compromise. What a routine needs depends on the
     * frequency it just declared, and neither JSON Schema nor the engine's grammar expresses
     * "required only when `frequency` is `semanal`" without a `oneOf` over two whole object
     * shapes. That buys a much more fragile grammar for the one guarantee it would add, and if
     * `ResponseFormat.json()` ever refused it the extractor would fall back to unconstrained
     * generation for every user with nothing but a log line to show for it. So the prompt asks for
     * the right field per frequency, and the parser's degradation stays as the net: omitting
     * `days` is still valid JSON, and still turns a weekly routine into a daily one.
     *
     * `minItems: 1` on `days` because an empty array is worth no more than an absent one. The
     * interval carries **no bounds**: `ParseAiRoutinesUseCase` already clamps it to 1..365, and
     * every construct in a schema becomes grammar the engine compiles on the device, where the
     * shopping categories have just shown what an over-built one costs. Bounds here would buy a
     * guarantee the parser already gives.
     *
     * Day names are **unaccented ASCII** on purpose: they never reach the user — the parser maps
     * them onto `Calendar` constants, accents and case included — so there is no reason to push
     * multi-byte characters through a grammar the engine compiles on every request. The shopping
     * categories below have no such freedom.
     */
    const val ROUTINES = """{
      "type": "object",
      "properties": {
        "routines": {
          "type": "array",
          "maxItems": 10,
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string" },
              "frequency": { "type": "string", "enum": ["diaria", "semanal", "cada_n_dias"] },
              "days": {
                "type": "array",
                "minItems": 1,
                "maxItems": 7,
                "items": {
                  "type": "string",
                  "enum": ["lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"]
                }
              },
              "interval_days": { "type": "integer" }
            },
            "required": ["title", "frequency"],
            "additionalProperties": false
          }
        }
      },
      "required": ["routines"],
      "additionalProperties": false
    }"""

    /**
     * Shopping list extraction.
     *
     * `quantity` as an integer and `unit` as an enum remove the two things the shopping parser
     * spends most of its tolerance on: `"2 kg"` arriving in the number field, and an invented
     * unit. The enum is the app's own [UNITS] list, verified by the test.
     *
     * **`category` is a closed list of keys**, one per category the app offers, resolved into the
     * real name by [AiShoppingCategories]. It used to be a free string while the prompt dictated
     * seven categories of its own invention, only three of which the app has: the assistant had
     * been creating products whose category the picker does not offer and the pantry then shows as
     * a heading of its own.
     *
     * The keys are **one ASCII word each, no spaces and no accents**, and that is not tidiness: an
     * enum of the categories as the user reads them does not survive this runtime. On LFM3 it dies
     * mid-answer, deterministically, at the first value with a space in it —
     *
     * ```
     * "category":"Lácteos"},{"name":"Tomates",…,"category":"Frutas‧Ġy
     * Error: token "Ġy" doesn't satisfy the grammar; forced bytes: got ' '
     * Stop: ParserTooComplex
     * ```
     *
     * — so the accents were never the problem and the spaces were: once the model has written
     * `Frutas`, only one enum member can still match and the engine switches to forcing the rest
     * byte by byte, which is where it breaks. Ten multi-word values also pushed the lexer into
     * `ParserTooComplex`. Single words keep the grammar small and every value reachable as whole
     * tokens. Nothing above a unit test could have caught this: the grammar is compiled by the
     * engine, on the device, against that model's tokenizer.
     *
     * Closing the enum is only safe because of the **`otros`** key. Without that escape hatch,
     * forcing a choice from a closed vocabulary would have promoted "usually wrong" to "guaranteed
     * wrong", which is the one thing a schema must not do. Products created before this keep the
     * category they were given — nothing breaks, the picker simply does not offer it.
     *
     * `maxItems` is a safety valve rather than a product rule — the shopping parser has no cap of
     * its own, and the KV window would give out long before thirty items.
     */
    const val SHOPPING = """{
      "type": "object",
      "properties": {
        "shopping_list": {
          "type": "array",
          "maxItems": 30,
          "items": {
            "type": "object",
            "properties": {
              "name": { "type": "string" },
              "quantity": { "type": "integer" },
              "unit": {
                "type": "string",
                "enum": ["unidad", "kg", "g", "L", "ml", "docena", "paquete"]
              },
              "category": {
                "type": "string",
                "enum": [
                  "frutas",
                  "lacteos",
                  "carnes",
                  "panaderia",
                  "bebidas",
                  "limpieza",
                  "snacks",
                  "congelados",
                  "especias",
                  "otros"
                ]
              }
            },
            "required": ["name", "quantity", "unit", "category"],
            "additionalProperties": false
          }
        }
      },
      "required": ["shopping_list"],
      "additionalProperties": false
    }"""
}
