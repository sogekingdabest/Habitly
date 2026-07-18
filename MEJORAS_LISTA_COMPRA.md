# Mejoras — Lista de la compra

> Análisis de mercado y propuestas · 2026-07-18
> Serie: [MEJORAS_RUTINAS.md](MEJORAS_RUTINAS.md) · [MEJORAS_ASISTENTE_IA.md](MEJORAS_ASISTENTE_IA.md) · Plan de ejecución: [PLAN_IMPLEMENTACION_TOP5.md](PLAN_IMPLEMENTACION_TOP5.md)

---

## 1. Estado actual de Habitly

Lo que la lista ya hace hoy (verificado en código):

- **Lista compartida en tiempo real** por casa (Firestore `households/{id}/shopping_items`, listener en `ShoppingRepositoryImpl`).
- **Items ricos**: nombre, cantidad, unidad, categoría, notas, tienda, autor (`ShoppingItem`).
- **Tiendas**: Mercadona, Lidl, Carrefour, Cualquiera + tiendas personalizadas por casa (`customStores` en el doc de la casa). Filtro y agrupación por tienda en la UI.
- **Progreso** (X/Y comprados), marcar todo, borrar comprados.
- **Historial**: archivar lista → `shopping_history`; restaurar una lista del historial.
- **Items frecuentes**: top 8 del historial como chips de añadido rápido (`GetFrequentItemsUseCase`).
- **Undo** al borrar (snackbar).
- **Pantalla de alta** con categoría y notas (FAB → formulario).
- **Integración IA**: el asistente propone listas (`@@LISTA@@`) y las vuelca en batch (`AddAiItemsToShoppingListUseCase`).
- **Widget** Glance con la compra pendiente.

---

## 2. Qué hace el mercado

### Referentes

| App | Punto fuerte | Punto débil |
|---|---|---|
| **Bring!** | Interfaz visual de iconos, plantillas, tab "Inspiración" con recetas, voz, integración Alexa, tarjetas de fidelización | Sin precios (muy reclamado), gestiona mal items de nombre parecido (duplicados que se pisan), iconos insuficientes para productos locales |
| **AnyList** | Categorización automática (escribes "leche" → pasillo lácteos), recetas→lista en un tap, import de recetas web, sync ~1,7 s | Lo mejor está en el tier de pago; enfocado a mercado USA |
| **Listonic** | Aprende tus hábitos y **predice** lo que necesitas, precios y total estimado, orden por pasillo, dictado por voz | Anuncios patrocinados dentro de la lista en el tier gratis |
| **OurGroceries** | Fotos en items ("compra ESTA marca"), escáner de código de barras, sync muy fiable y barato | Interfaz anticuada, sin extras |
| **Flatastic** | Todo-en-uno para pisos compartidos: lista + tareas + **reparto de gastos** | Poco pulido individual; la lista es lo más flojo |

### Huecos detectados (lo que la gente pide y casi nadie da)

1. **Listas conscientes de la despensa**: cruzar lo que vas a comprar con lo que ya hay en casa. Los análisis de 2026 lo señalan como *el* mayor diferenciador sin cubrir.
2. **Precios/presupuesto** sin anuncios ni suscripción abusiva.
3. **Saber quién está comprando ahora** y coordinar en tiempo real (todas sincronizan, ninguna comunica presencia).
4. **Duplicados bien resueltos** (sumar cantidades en vez de pisar o duplicar).

---

## 3. Propuestas

### Corto plazo (quick wins)

- **Autocompletado predictivo al escribir.** Extender `GetFrequentItemsUseCase`: sugerir mientras se teclea y **recordar categoría/unidad/tienda de la última vez** que se compró ese producto. AnyList usa una BD estática; Habitly puede aprender del historial real de la casa.
- **Detección de duplicados al añadir.** Si "tomate" ya está pendiente, ofrecer sumar cantidad. Queja documentada de Bring!; barato y se nota.
- **Plantillas de lista.** "Guardar como plantilla" (desde lista actual o historial) + "Nueva lista desde plantilla". `restoreHistory` ya hace el 80% del trabajo.

### Medio plazo

- **Modo "comprando"**: vista para la tienda con pantalla siempre encendida, tipografía grande, agrupado por **categoría** (el campo existe, hoy solo se agrupa por tienda) y presencia "Dani está comprando ahora" para el resto de la casa (Firestore en tiempo real ya está montado; nadie del mercado lo hace bien).
- **Precio estimado y presupuesto por lista.** Campo `price` opcional + total arriba. De lo más pedido del mercado. Candidato natural a Habitly Plus (fase 4 de la auditoría).
- **Fotos en items** (estilo OurGroceries): resuelve el "no era esta marca" entre miembros.
- **⭐ Despensa ligera** (TOP 5): al archivar la compra, lo comprado pasa a una despensa simple ("esto hay en casa", sin caducidades ni escaneos en el MVP). Combinada con la IA on-device habilita "¿qué ceno con lo que tengo?" privado y offline. Detalle en [PLAN_IMPLEMENTACION_TOP5.md](PLAN_IMPLEMENTACION_TOP5.md) (fase 4).

### Largo plazo / ambicioso

- **Escaneo de ticket** (OCR del ticket de Mercadona/Lidl) para poblar historial, precios y despensa de golpe.
- **Escáner de código de barras** para altas rápidas con nombre canónico.
- **Reparto de gastos** estilo Flatastic (quién pagó qué). Ojo al scope creep: solo si el público objetivo se desplaza a pisos compartidos.

---

## 4. Relación con el TOP 5

De este documento entra en el top 5: **Despensa ligera + "qué cocino con lo que tengo"** (fase 4 del plan). El autocompletado predictivo y las plantillas son los siguientes en cola por ratio valor/esfuerzo.

---

## Fuentes

- [SmartCart Family — comparativa Listonic/Bring/AnyList/OurGroceries](https://smartcartfamily.com/en/blog/grocery-apps-comparison)
- [BestApp — The Best Grocery List Apps of 2026](https://www.bestapp.com/best-grocery-list-apps/)
- [NerdWallet — Best Grocery List App](https://www.nerdwallet.com/finance/learn/best-grocery-list-apps)
- [Reseña Bring! 2026](https://marlvel.ai/apps/bring-grocery-shopping-list)
- [Listonic — comparativas oficiales](https://listonic.com/compare-apps)
- [FoodiePrep — meal planning apps con listas integradas](https://www.foodieprep.ai/blog/meal-planning-apps-with-builtin-grocery-lists-a-2026-sidebyside-review)
