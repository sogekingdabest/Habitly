# Habitly

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-29+-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.03-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

**Smart household management powered by on-device AI**

[English](#english) · [Español](#español) · [Galego](#galego)

</div>

---

## English

### About

Habitly is a modern Android app designed to simplify household coordination. Manage shopping lists, organize routines, and get AI-powered suggestions — all running locally on your device with Firebase sync for seamless multi-user collaboration.

### Features

- **Authentication** — Email/password login & registration, Google Sign-In, email verification, and password recovery
- **Household Management** — Create or join households with invite codes, multi-user support with member management
- **Shopping List** — Add, edit, and remove items in real-time, mark items as purchased, and view shopping history
- **Routines** — Create and manage household routines, track recurring tasks
- **AI Assistant** — 100% on-device inference with LiteRT-LM, downloadable models (Gemma 4), generate recipe suggestions and auto-generate shopping lists, persistent chat sessions with Room database

### Tech Stack

| Category | Technology |
|---|---|
| **Language** | Kotlin 2.3.20 |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | Clean Architecture + MVI |
| **Dependency Injection** | Hilt |
| **Backend** | Firebase Auth + Firestore |
| **Local Database** | Room |
| **AI/ML** | LiteRT-LM (on-device inference) |
| **Async** | Coroutines + Flow |
| **Navigation** | Navigation Compose + Hilt Navigation |
| **Testing** | JUnit + Hilt Testing + Coroutines Test |

### Architecture

The project follows Clean Architecture principles with feature-based modules. Each feature is organized into three layers:

- **`data/`** — Repository implementations, data sources (local & remote)
- **`domain/`** — Models, repository interfaces, use cases
- **`presentation/`** — Compose screens, ViewModels, UI state/events

```
app/src/main/java/com/monsteraltech/habitly/
├── di/                          # Hilt dependency injection modules
├── feature/
│   ├── aiassistant/             # AI chat assistant with on-device models
│   ├── dashboard/               # Home dashboard
│   ├── household/               # Household & member management
│   ├── login/                   # Authentication (email + Google)
│   ├── main/                    # Main screen with bottom navigation
│   ├── register/                # Registration & email verification
│   ├── routines/                # Routine management
│   └── shopping/                # Shopping list & history
├── navigation/                  # Navigation graphs
└── ui/theme/                    # Compose theming (colors, typography)
```

### Requirements

- **Min SDK:** 29 (Android 10)
- **Target SDK:** 36
- **JDK:** 21

### Setup

1. Clone the repository
2. Create a Firebase project with Auth (Email/Password + Google) and Firestore enabled
3. Download `google-services.json` and place it in the `app/` directory
4. Open in Android Studio and run

### Author

**Daniel Olañeta**

---

## Español

### Acerca de

Habitly es una aplicación Android moderna diseñada para simplificar la coordinación del hogar. Gestiona listas de la compra, organiza rutinas y obtén sugerencias impulsadas por IA — todo funcionando localmente en tu dispositivo con sincronización Firebase para colaboración entre múltiples usuarios.

### Funcionalidades

- **Autenticación** — Inicio de sesión y registro con email/contraseña, Google Sign-In, verificación de email y recuperación de contraseña
- **Gestión del Hogar** — Crea o únete a hogares con códigos de invitación, soporte multiusuario con gestión de miembros
- **Lista de la Compra** — Añade, edita y elimina artículos en tiempo real, marca artículos como comprados y consulta el historial
- **Rutinas** — Crea y gestiona rutinas del hogar, haz seguimiento de tareas recurrentes
- **Asistente de IA** — Inferencia 100% local con LiteRT-LM, modelos descargables (Gemma 4), genera sugerencias de recetas y listas de la compra automáticas, sesiones de chat persistentes con base de datos Room

### Tecnologías

| Categoría | Tecnología |
|---|---|
| **Lenguaje** | Kotlin 2.3.20 |
| **UI** | Jetpack Compose + Material 3 |
| **Arquitectura** | Clean Architecture + MVI |
| **Inyección de Dependencias** | Hilt |
| **Backend** | Firebase Auth + Firestore |
| **Base de Datos Local** | Room |
| **IA/ML** | LiteRT-LM (inferencia local) |
| **Asincronía** | Coroutines + Flow |
| **Navegación** | Navigation Compose + Hilt Navigation |
| **Testing** | JUnit + Hilt Testing + Coroutines Test |

### Arquitectura

El proyecto sigue los principios de Clean Architecture con módulos basados en funcionalidades. Cada funcionalidad se organiza en tres capas:

- **`data/`** — Implementaciones de repositorios, fuentes de datos (local y remota)
- **`domain/`** — Modelos, interfaces de repositorio, casos de uso
- **`presentation/`** — Pantallas Compose, ViewModels, estado/eventos de UI

```
app/src/main/java/com/monsteraltech/habitly/
├── di/                          # Módulos de inyección de dependencias Hilt
├── feature/
│   ├── aiassistant/             # Asistente de IA con modelos locales
│   ├── dashboard/               # Panel principal
│   ├── household/               # Gestión del hogar y miembros
│   ├── login/                   # Autenticación (email + Google)
│   ├── main/                    # Pantalla principal con navegación inferior
│   ├── register/                # Registro y verificación de email
│   ├── routines/                # Gestión de rutinas
│   └── shopping/                # Lista de la compra e historial
├── navigation/                  # Grafos de navegación
└── ui/theme/                    # Theming de Compose (colores, tipografía)
```

### Requisitos

- **Min SDK:** 29 (Android 10)
- **Target SDK:** 36
- **JDK:** 21

### Configuración

1. Clona el repositorio
2. Crea un proyecto Firebase con Auth (Email/Contraseña + Google) y Firestore activados
3. Descarga `google-services.json` y colócalo en el directorio `app/`
4. Abre en Android Studio y ejecuta

### Autor

**Daniel Olañeta**

---

## Galego

### Acerca de

Habitly é unha aplicación Android moderna deseñada para simplificar a coordinación do fogar. Xestiona listas da compra, organiza rutinas e obtén suxestións impulsadas por IA — todo funcionando localmente no teu dispositivo con sincronización Firebase para colaboración entre múltiples usuarios.

### Funcionalidades

- **Autenticación** — Inicio de sesión e rexistro con email/contrasinal, Google Sign-In, verificación de email e recuperación de contrasinal
- **Xestión do Fogar** — Crea ou únete a fogares con códigos de invitación, soporte multiusuario con xestión de membros
- **Lista da Compra** — Engade, edita e elimina artigos en tempo real, marca artigos como comprados e consulta o historial
- **Rutinas** — Crea e xestiona rutinas do fogar, fai seguimento de tarefas recorrentes
- **Asistente de IA** — Inferencia 100% local con LiteRT-LM, modelos descargables (Gemma 4), xera suxestións de receitas e listas da compra automáticas, sesións de chat persistentes con base de datos Room

### Tecnoloxías

| Categoría | Tecnoloxía |
|---|---|
| **Linguaxe** | Kotlin 2.3.20 |
| **UI** | Jetpack Compose + Material 3 |
| **Arquitectura** | Clean Architecture + MVI |
| **Inxección de Dependencias** | Hilt |
| **Backend** | Firebase Auth + Firestore |
| **Base de Datos Local** | Room |
| **IA/ML** | LiteRT-LM (inferencia local) |
| **Asincronía** | Coroutines + Flow |
| **Navegación** | Navigation Compose + Hilt Navigation |
| **Testing** | JUnit + Hilt Testing + Coroutines Test |

### Arquitectura

O proxecto segue os principios de Clean Architecture con módulos baseados en funcionalidades. Cada funcionalidade organízase en tres capas:

- **`data/`** — Implementacións de repositorios, fontes de datos (local e remota)
- **`domain/`** — Modelos, interfaces de repositorio, casos de uso
- **`presentation/`** — Pantallas Compose, ViewModels, estado/eventos de UI

```
app/src/main/java/com/monsteraltech/habitly/
├── di/                          # Módulos de inxección de dependencias Hilt
├── feature/
│   ├── aiassistant/             # Asistente de IA con modelos locais
│   ├── dashboard/               # Panel principal
│   ├── household/               # Xestión do fogar e membros
│   ├── login/                   # Autenticación (email + Google)
│   ├── main/                    # Pantalla principal con navegación inferior
│   ├── register/                # Rexistro e verificación de email
│   ├── routines/                # Xestión de rutinas
│   └── shopping/                # Lista da compra e historial
├── navigation/                  # Grafos de navegación
└── ui/theme/                    # Theming de Compose (cores, tipografía)
```

### Requisitos

- **Min SDK:** 29 (Android 10)
- **Target SDK:** 36
- **JDK:** 21

### Configuración

1. Clona o repositorio
2. Crea un proxecto Firebase con Auth (Email/Contrasinal + Google) e Firestore activados
3. Descarga `google-services.json` e colócao no directorio `app/`
4. Abre en Android Studio e executa

### Autor

**Daniel Olañeta**
