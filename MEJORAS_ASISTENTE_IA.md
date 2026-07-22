# Mejoras — Asistente IA

> Análisis de mercado y propuestas · 2026-07-18
> Serie: [MEJORAS_LISTA_COMPRA.md](MEJORAS_LISTA_COMPRA.md) · [MEJORAS_RUTINAS.md](MEJORAS_RUTINAS.md) · Plan de ejecución: [PLAN_IMPLEMENTACION_TOP5.md](PLAN_IMPLEMENTACION_TOP5.md)

---

## 1. Estado actual de Habitly

- **100% on-device** con LiteRT-LM: Gemma 4 E2B (2,6 GB), Gemma 4 E4B (3,7 GB). Descarga robusta vía WorkManager (sobrevive a navegación y muerte de proceso).
- **Chat** con streaming, markdown, historial de sesiones en Room, cambio de modelo por sesión.
- **Contexto oculto** (`GetAiContextUseCase`): personalidad base + lista de la compra (solo nombre y estado) + rutinas **personales** (solo título y si están marcadas).
- **Acción sobre la app**: marcador `@@LISTA@@` + JSON → parser tolerante (`ParseAiShoppingListUseCase`, testeado, con fallback regex) → tarjeta "Añadir a la lista" → batch a Firestore. La UI oculta el bloque (`AiShoppingListFormat.stripFromDisplay`).
- **Quick prompts** estáticos: menú semanal, recetas con pollo, lista semanal, recetas vegetarianas, cena rápida.

### Fricción principal detectada

Hay que descargar **1,6–3,7 GB** antes de poder usar el asistente por primera vez. Es la mayor barrera de adopción de la feature.

### Margen de mejora inmediato en el contexto

`GetAiContextUseCase` hoy **no** incluye: rutinas de casa (solo personales), cantidades/unidades/categorías/tiendas de la lista, fecha y día de la semana, rachas, ni miembros de la casa. El modelo responde "a ciegas" sobre todo eso.

---

## 2. Qué hace el mercado

### Referentes (planificadores con IA, 2026)

| App | Punto fuerte | Punto débil |
|---|---|---|
| **FoodiePrep** | Plan semanal end-to-end desde dieta/alergias/tamaño del hogar/despensa; lista categorizada automática | Cloud + suscripción |
| **Recipy** | Una sola capa de IA unifica recetas + plan + despensa + lista; "qué hago con lo que tengo" | Cloud; modelo de negocio incierto (hoy gratis) |
| **Melio** | Planificación multi-persona real (padres comparten plato, niños distinto), lista unificada del hogar | Cloud + suscripción |
| **Eat This Much / Ollie** | Automatización total del plan según macros/preferencias | Rígidos, poco conversacionales |
| **Paprika** | Gold standard en recetario (scrapea webs, limpia anuncios) | Sin IA generativa; sin hogar compartido |

Patrones comunes: **perfil del hogar** (dieta, alergias, nº comensales) como base de todo, plan semanal → lista de la compra en un tap, y "qué cocino con lo que tengo" como feature estrella. Todos cobran suscripción por la parte de IA (coste por token que Habitly no tiene).

### Tendencia on-device

La demanda de soberanía de datos está en máximos en 2026; Gemini Nano/ML Kit GenAI, Apple Intelligence y los NPU de Samsung empujan el patrón "IA local por defecto". Beneficios que Habitly ya tiene: privacidad por defecto, sin latencia de red, offline, coste marginal cero. **La apuesta de Habitly ya está validada por el mercado; lo que falta es sacarle partido.**

---

## 3. Propuestas

### Corto plazo (quick wins)

- **⭐ Contexto enriquecido** (TOP 5): añadir a `GetAiContextUseCase` las rutinas de casa, cantidades/tiendas/categorías de la lista, fecha y día de la semana, rachas y nombres de miembros. Es la diferencia entre un chatbot genérico y *tu* asistente ("mañana te toca cambiar sábanas y llevas 12 días de racha").
- **⭐ Quick prompts contextuales** (TOP 5): calcularlos según estado — domingo → "Planifica el menú de la semana"; lista con 15 items → "¿Qué recetas salen de mi lista?"; despensa llena → "¿Qué ceno con lo que tengo?".
- **Dictado por voz** en el input del chat (`SpeechRecognizer` del sistema; sin dependencias nuevas).

### Medio plazo

- **⭐ `@@RUTINA@@` — crear rutinas desde el chat** (TOP 5). Replicar la infraestructura ya probada de `@@LISTA@@` (marcador + parser tolerante + tarjeta con botón): "proponme un plan de limpieza semanal" → tarjeta "Crear 5 rutinas" → se crean con frecuencia y días. Convierte al asistente en el mejor onboarding de la app.
- **Perfil del hogar para la IA**: dieta, alergias, nº de comensales, presupuesto → se inyecta al system prompt. La base de todos los meal planners de pago; para Habitly son cuatro campos en Firestore.
- **Menú semanal persistente**: hoy el menú muere en el chat. Guardarlo como plan lunes→domingo visible en el dashboard, con ingredientes conectados a la lista vía la infra existente. Cozi/Maple lo hacen manual; el de Habitly sería IA-first y privado.
- **Recetario local**: botón "Guardar receta" sobre respuestas del asistente (Room ya está montado); con el tiempo, "recetas de la casa" reutilizables por la IA.

### Largo plazo / ambicioso

- **Reducir la fricción de descarga**:
  - Modelo "Ligero" ~0,5 GB (p. ej. Gemma 1B en LiteRT) para la primera experiencia.
  - **Gemini Nano vía ML Kit GenAI** como opción de 0 bytes de descarga en dispositivos compatibles (sigue siendo on-device → mantiene la promesa de privacidad).
  - Descarga solo con Wi-Fi por defecto + valor visible antes de descargar (quick prompts de ejemplo con respuestas precocinadas).
- **Resumen semanal proactivo** generado por IA (notificación local, sin backend): "tu semana: rachas, compra, sugerencia de menú".
- **Híbrido cloud opcional** (API de pago para quien quiera más calidad, encajaría con Habitly Plus). ⚠️ Recomendación: pensarlo dos veces — el 100% local es identidad de marca; hay mejores candidatos para Plus (presupuesto de compra, estadísticas avanzadas, perfil IA avanzado, plantillas ilimitadas).

---

## 4. Relación con el TOP 5

De este documento entran en el top 5: **contexto enriquecido + quick prompts contextuales** (fase 1 del plan) y **`@@RUTINA@@`** (fase 3). La despensa (fase 4, en [MEJORAS_LISTA_COMPRA.md](MEJORAS_LISTA_COMPRA.md)) también alimenta directamente al asistente.

---

## Fuentes

- [FoodiePrep — The 10 Best Meal Planning Apps in 2026](https://www.foodieprep.ai/blog/meal-planning-apps-in-2026-which-tools-actually-simplify-your-kitchen)
- [Recipy — Best AI Meal Planning Apps 2026](https://recipyapp.com/blog/best-ai-meal-planning-apps-2026)
- [Melio — Best AI Meal Planning Apps](https://meal-plan.app/en/resources/guides/best-ai-meal-planning-apps/)
- [PlanEat AI — Family meal planning 2026](https://www.planeatai.com/blog/family-meal-planning-app-2026)
- [Local AI Master — Gemini Nano Android Guide 2026](https://localaimaster.com/blog/gemini-nano-android-guide)
- [Newly — On-Device AI Mobile Apps: A Practical 2026 Guide](https://newly.app/guides/on-device-ai-mobile-apps)
- [Google — ML Kit GenAI APIs](https://developers.google.com/ml-kit/genai)
