# Mejoras — Rutinas

> Análisis de mercado y propuestas · 2026-07-18
> Serie: [MEJORAS_LISTA_COMPRA.md](MEJORAS_LISTA_COMPRA.md) · [MEJORAS_ASISTENTE_IA.md](MEJORAS_ASISTENTE_IA.md) · Plan de ejecución: [PLAN_IMPLEMENTACION_TOP5.md](PLAN_IMPLEMENTACION_TOP5.md)

---

## 1. Estado actual de Habitly

- **Dos tipos**: rutinas personales (`users/{uid}/routines`) y de casa (`households/{id}/routines`), observadas en un flow combinado.
- **Frecuencias**: diaria, semanal y personalizada por días fijos de la semana (`scheduledDays` con constantes de `Calendar.DAY_OF_WEEK`).
- **Rachas**: cada completado se guarda en la subcolección `completions/{yyyy-MM-dd}` (con `date`, `userId`, `completedAt`); `StreakCalculator` (puro, testeado) recalcula `currentStreak`/`bestStreak` denormalizados en la rutina. Badge 🔥 con racha ≥ 2.
- **Recordatorios**: WorkManager (`RoutineReminderWorker`) con hora configurable que respeta `scheduledDays`; deep link de la notificación a la pestaña Rutinas.
- **Quién la hizo**: `lastCompletedBy` + nicknames de miembros ya resueltos en `RoutinesUiState.memberNicknames`.
- **Gestión**: crear, editar, borrar, reordenar manualmente, undo al completar desde el dashboard.
- **Widget** con las rutinas de hoy.

### Limitación técnica detectada

`StreakCalculator` cuenta **días de calendario consecutivos**. Una rutina semanal (p. ej. solo lunes) nunca puede pasar de racha 1, porque entre dos completados siempre hay días "vacíos". El badge 🔥 (≥2) es inalcanzable para rutinas no diarias. Cualquier mejora de rachas debe empezar por hacer el cálculo **consciente del calendario programado** (racha = ocurrencias programadas consecutivas cumplidas).

---

## 2. Qué hace el mercado

### Referentes

| App | Punto fuerte | Punto débil |
|---|---|---|
| **Loop Habit Tracker** | Gratis/open source, offline, privacidad, "habit strength" (fuerza del hábito) en vez de racha binaria, gráficas | Individual, sin nada social/hogar |
| **Streaks** (iOS) | Ultra rápido de marcar, schedules flexibles ("X veces por semana"), integración salud | Solo Apple; la racha sigue siendo el centro |
| **HabitNow** (Android) | Frecuencias muy flexibles (diaria/semanal/mensual, X veces por periodo), estadísticas completas, widgets | Individual; pago único para desbloquear |
| **Habitica** | Gamificación RPG completa, social (parties, retos) | Abrumadora; la estética no es para todo el mundo |
| **Sweepy** | Tareas de limpieza por habitación, puntos y leaderboard, reparto equitativo automático | El ranking competitivo quema a parte de los usuarios |
| **Tody** | "Suciedad" que se acumula con el tiempo (en vez de fechas rígidas), frecuencia "cada N días", rotación de responsables | Solo limpieza; sin hábitos personales |
| **OurHome / Flatastic** | Puntos y recompensas en familia / reparto entre compañeros de piso | Genéricas, poco cuidadas |

### Lo que dice la investigación sobre abandono

- **~90% abandona el tracker en 30–60 días.**
- Causa nº 1: **ansiedad de racha** — romper la racha desmoraliza ("ya he fallado, para qué") y provoca el cierre definitivo de la app.
- Causa nº 2: **rigidez** — la vida real no encaja en "todos los días a las 8"; el tracker que te hace sentir culpable se desinstala.
- Causa nº 3: demasiados hábitos a la vez y demasiada fricción para marcar.
- Factor de retención: **accountability social** (+65% de adherencia con compañero; Habitly lo tiene gratis: la casa).

---

## 3. Propuestas

### Corto plazo (quick wins)

- **⭐ Heatmap de calendario por rutina** (TOP 5). Los datos ya existen (`completions/{yyyy-MM-dd}`); es solo UI: calendario mensual con días marcados + tasa de cumplimiento. La feature de estadísticas con mejor ratio valor/esfuerzo.
- **⭐ Protector de racha** (TOP 5). Permitir 1 fallo sin romper la racha y/o modo vacaciones (días que no cuentan). Ataca directamente la causa nº 1 de abandono. `StreakCalculator` es puro y testeado: el sitio perfecto para esta regla.
- **⭐ Frecuencia "cada N días"** (TOP 5). "Cambiar sábanas cada 10 días" no se puede modelar hoy. Es la frecuencia estrella de Tody para hogar.

### Medio plazo

- **Frecuencia "X veces por semana"** ("gimnasio 3 veces, me da igual qué días"): elimina la culpa del día fallado. Más compleja (la racha pasa a ser semanal), por eso va detrás de "cada N días".
- **⭐ Rotación y asignación de rutinas de casa** (TOP 5). `lastCompletedBy` + miembros ya están: "esta semana le toca a X", rotación automática al completar. Es LA feature por la que se instalan Sweepy/Flatastic, y Habitly tiene el household nativo que a ellas les falta.
- **⭐ Balance del hogar** (TOP 5). Resumen semanal de quién completó cuántas rutinas de casa. Deliberadamente **sin leaderboard competitivo**: la investigación indica que los rankings motivan a unos y queman a otros; mejor un balance neutro + hitos cooperativos ("la casa completó 50 rutinas este mes").
- **Estadísticas**: mejor día de la semana, % de cumplimiento por rutina, evolución mensual.

### Largo plazo / ambicioso

- **Rutinas por zona** (Cocina, Baño…) como etiqueta opcional, estilo Tody; habilita planes de limpieza por habitación generados por la IA.
- **Hitos cooperativos de casa** (logros compartidos, no competitivos).
- **Notificación push a la casa** cuando alguien completa ("Dani ha sacado la basura 🎉") — requiere FCM + Cloud Functions; ya diferido conscientemente en la auditoría. La rotación funciona sin esto.

---

## 4. Relación con el TOP 5

De este documento entran **dos** entradas del top 5:
- **Heatmap + protector de racha + "cada N días"** → fase 2 del plan.
- **Rotación + balance del hogar** → fase 5 del plan.

---

## Fuentes

- [Zapier — The 5 best habit tracker apps](https://zapier.com/blog/best-habit-tracker-app/)
- [Reclaim — The 10 Best Habit Tracker Apps of 2026](https://reclaim.ai/blog/habit-tracker-apps)
- [Stelo — Why most habit trackers fail](https://steloapp.io/blog-why-habit-trackers-fail)
- [Moore Momentum — Why 90% quit habit trackers within 30 days](https://mooremomentum.com/blog/why-do-90-of-people-quit-habit-trackers-within-30-days/)
- [Pattrn — The problem with habit trackers](https://pattrn.io/blog/what-is-the-problem-with-habit-trackers-and-how-you-can-solve-it)
- [Plastnofy — The Best Apps for Household Chores in 2026](https://plastnofy.com/articles/the-best-apps-for-household-chores-in-2026)
- [Rent — Best chore apps for roommates](https://www.rent.com/blog/best-apps-for-roommates/)
