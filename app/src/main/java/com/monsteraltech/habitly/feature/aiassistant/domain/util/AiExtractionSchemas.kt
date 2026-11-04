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
 * together; `AiExtractionSchemasTest` fails the build if a schema stops being valid JSON or stops
 * agreeing with what the parsers and the app actually accept.
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
              "frequency": { "type": "string", "enum": ["diaria", "semanal", "cada_n_dias"] }
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
     * **`category` is a plain string on purpose.** The seven categories the prompt dictates are
     * not the app's categories — `CATEGORIES` in `QuickAddSheet.kt` holds ten different ones, and
     * only three names appear in both. Nothing validates or maps the value: it goes straight into
     * the product, so the assistant already creates products whose category the picker does not
     * offer. That is a real bug, but it is the prompt's bug and it predates this schema. Turning
     * the current list into an enum would promote it from "usually wrong" to "guaranteed wrong",
     * which is the one thing a schema must not do. Left as a free string until the vocabulary is
     * decided; when it is, this becomes an enum over the app's list and the prompt follows.
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
              "category": { "type": "string" }
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
