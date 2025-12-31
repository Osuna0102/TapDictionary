# Especificación Técnica - MVP Dictionary Overlay App (Android)

## 1. Resumen Ejecutivo

**Nombre del Proyecto:** GodTap Dictionary (AndroidGodTap)

**Objetivo:** Crear un MVP funcional que permita a los usuarios seleccionar texto en cualquier aplicación de Android y mostrar automáticamente un diccionario emergente con traducciones de palabras japonesas utilizando JMDICT.

**Plataforma:** Android (API 24+, Android 7.0+)

**Stack Tecnológico:**
- Lenguaje: Kotlin
- UI Framework: Jetpack Compose
- Base de datos: Room (SQLite) para JMDICT local
- Servicio de accesibilidad: AccessibilityService
- Overlay: WindowManager con TYPE_APPLICATION_OVERLAY

---

## 2. Arquitectura del Sistema

### 2.1 Componentes Principales

```
┌─────────────────────────────────────────────┐
│          MainActivity                        │
│  - Control de permisos                      │
│  - Activación/desactivación del servicio   │
│  - Configuración inicial                    │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│     TextSelectionAccessibilityService       │
│  - Monitorea eventos de selección de texto │
│  - Captura texto seleccionado               │
│  - Detecta tap en texto                     │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│        JapaneseTokenizer                     │
│  - Tokeniza texto japonés                   │
│  - Identifica palabras/frases relevantes    │
│  - Prioriza tokens más largos               │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│         DictionaryRepository                 │
│  - Consulta base de datos JMDICT            │
│  - Cache en memoria para velocidad          │
│  - Búsqueda optimizada                      │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│          OverlayManager                      │
│  - Muestra popup flotante                   │
│  - Posiciona cerca del texto seleccionado   │
│  - Gestiona interacciones del usuario       │
└─────────────────────────────────────────────┘
```

---

## 3. Funcionalidades del MVP

### 3.1 Permisos Requeridos

#### AndroidManifest.xml
```xml
<!-- Permiso para overlay flotante -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Permiso para servicio de accesibilidad -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE"
    tools:ignore="ProtectedPermissions" />

<!-- Permiso para internet (si se necesita descargar JMDICT) -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Permiso para almacenamiento (para JMDICT local) -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

#### Flujo de solicitud de permisos:

1. **SYSTEM_ALERT_WINDOW**: Solicitar mediante `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
2. **BIND_ACCESSIBILITY_SERVICE**: Usuario debe activar manualmente en Configuración > Accesibilidad

---

### 3.2 Servicio de Accesibilidad

**Clase:** `TextSelectionAccessibilityService`

**Responsabilidades:**
- Detectar eventos de selección de texto (`TYPE_VIEW_TEXT_SELECTION_CHANGED`)
- Detectar eventos de texto clickeable (`TYPE_VIEW_CLICKED`)
- Extraer el texto seleccionado
- Filtrar contextos irrelevantes (campos de contraseña, teclados, etc.)

**Configuración XML (accessibility_service_config.xml):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewTextSelectionChanged|typeViewClicked|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="@null" />
```

**Eventos clave a monitorear:**
- `AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED`
- `AccessibilityEvent.TYPE_VIEW_CLICKED` (para tap en texto)

**Extracción de texto:**
```kotlin
private fun extractSelectedText(event: AccessibilityEvent): String? {
    val text = event.text.firstOrNull()?.toString() ?: return null
    
    // Obtener rango de selección
    val start = event.fromIndex
    val end = event.toIndex
    
    if (start >= 0 && end > start && end <= text.length) {
        return text.substring(start, end)
    }
    
    return null
}
```

---

### 3.3 Tokenización de Texto Japonés

**Librería recomendada:** [Kuromoji](https://github.com/atilika/kuromoji) o [Sudachi](https://github.com/WorksApplications/Sudachi)

**Alternativa ligera para MVP:** Tokenización básica basada en patrones de caracteres japoneses.

**Clase:** `JapaneseTokenizer`

**Estrategia de tokenización:**

1. **Detectar tipo de texto:**
   - Hiragana: \u3040-\u309F
   - Katakana: \u30A0-\u30FF
   - Kanji: \u4E00-\u9FAF
   - Números japoneses: \uFF10-\uFF19

2. **Priorizar tokens más largos:**
   - Si se selecciona "食べました", tokenizar como: ["食べました", "食べる", "食", "べ", "ました"]
   - Buscar en JMDICT empezando por el token más largo

3. **Manejo de partículas y conjugaciones:**
   - Identificar patrones comunes (ます、ました、ている、etc.)
   - Reducir a forma base (食べました → 食べる)

**Implementación básica para MVP:**
```kotlin
class SimpleJapaneseTokenizer {
    
    fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        
        // Agregar texto completo primero
        tokens.add(text)
        
        // Generar sub-strings de mayor a menor longitud
        for (length in text.length - 1 downTo 1) {
            for (start in 0..text.length - length) {
                tokens.add(text.substring(start, start + length))
            }
        }
        
        return tokens.distinct()
    }
    
    fun findBestMatch(tokens: List<String>, dictionary: DictionaryRepository): DictionaryEntry? {
        // Buscar en orden de tokens (más largo primero)
        for (token in tokens) {
            dictionary.search(token)?.let { return it }
        }
        return null
    }
}
```

---

### 3.4 Base de Datos JMDICT

**Formato:** [JMdict](http://www.edrdg.org/jmdict/j_jmdict.html) (XML) convertido a SQLite

**Estructura de la base de datos:**

```sql
-- Tabla principal de entradas
CREATE TABLE dictionary_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kanji TEXT,                    -- 食べる
    reading TEXT NOT NULL,         -- たべる
    sense_glosses TEXT NOT NULL,   -- to eat (JSON array)
    pos TEXT,                      -- part of speech (verb, noun, etc.)
    frequency INTEGER DEFAULT 0,   -- frecuencia de uso
    jlpt_level INTEGER             -- nivel JLPT (N5=5, N1=1)
);

-- Índices para búsqueda rápida
CREATE INDEX idx_kanji ON dictionary_entries(kanji);
CREATE INDEX idx_reading ON dictionary_entries(reading);
CREATE INDEX idx_frequency ON dictionary_entries(frequency DESC);
```

**Optimización para MVP:**
- Incluir solo entradas comunes (frecuencia alta)
- Filtrar por JLPT N5-N3 para reducir tamaño
- Comprimir base de datos con SQLite compression

**Clase:** `DictionaryRepository`

```kotlin
class DictionaryRepository(private val database: AppDatabase) {
    
    private val cache = LruCache<String, DictionaryEntry>(100)
    
    suspend fun search(term: String): DictionaryEntry? {
        // Buscar en cache primero
        cache.get(term)?.let { return it }
        
        // Buscar en base de datos
        val entry = database.dictionaryDao().searchByKanji(term)
            ?: database.dictionaryDao().searchByReading(term)
        
        entry?.let { cache.put(term, it) }
        return entry
    }
}
```

---

### 3.5 Popup Overlay

**Clase:** `OverlayManager`

**Tipo de ventana:** `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`

**Características del popup:**
- Tamaño: Compacto (max 300dp ancho, alto variable)
- Posición: Cerca del texto seleccionado (encima si hay espacio)
- Transparencia: Fondo semi-transparente
- Interacción: Click fuera para cerrar, swipe para descartar

**Layout del popup:**
```
┌─────────────────────────────────┐
│  食べる (たべる)                 │
│  ─────────────────────           │
│  Verb (ichidan)                  │
│                                  │
│  1. to eat                       │
│  2. to consume                   │
│                                  │
│  [×]  Close                      │
└─────────────────────────────────┘
```

**LayoutParams para el overlay:**
```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = touchX
    y = touchY - popupHeight
}
```

**Gestión del ciclo de vida:**
- Mostrar al detectar selección válida
- Auto-cerrar después de 10 segundos de inactividad
- Cerrar al tocar fuera del popup
- Cerrar al seleccionar nuevo texto

---

## 4. Flujo de Usuario

### 4.1 Primera Ejecución

```
1. Usuario abre la app
   ↓
2. MainActivity muestra pantalla de bienvenida
   ↓
3. Solicitar permiso SYSTEM_ALERT_WINDOW
   ↓
4. Redirigir a Configuración de Accesibilidad
   ↓
5. Usuario activa el servicio de accesibilidad
   ↓
6. Confirmar que la base de datos JMDICT está lista
   ↓
7. Mostrar tutorial rápido (opcional)
   ↓
8. Usuario puede usar otras apps
```

### 4.2 Uso Normal

```
1. Usuario está leyendo en cualquier app (ej. Chrome, Kindle)
   ↓
2. Usuario selecciona/toca una palabra japonesa ("食べる")
   ↓
3. AccessibilityService detecta evento de selección
   ↓
4. Extraer texto seleccionado
   ↓
5. Verificar que contiene caracteres japoneses
   ↓
6. Tokenizar texto (食べる → [食べる, 食べ, 食, べる, べ, る])
   ↓
7. Buscar tokens en JMDICT (empezar por más largo)
   ↓
8. Encontrar coincidencia: "食べる" (to eat)
   ↓
9. Mostrar popup flotante con traducción
   ↓
10. Usuario lee traducción
   ↓
11. Usuario cierra popup o selecciona nueva palabra
```

### 4.3 Activación/Desactivación

**Opción 1: Notificación permanente**
```kotlin
// Mostrar notificación con botón toggle
val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("GodTap Dictionary")
    .setContentText("Active - Tap to disable")
    .setSmallIcon(R.drawable.ic_dictionary)
    .addAction(R.drawable.ic_toggle, "Disable", disablePendingIntent)
    .build()
```

**Opción 2: Quick Settings Tile (Android 7+)**
```kotlin
class DictionaryTileService : TileService() {
    override fun onClick() {
        val isActive = toggleDictionaryService()
        updateTileState(isActive)
    }
}
```

---

## 5. Requisitos de Rendimiento

### 5.1 Velocidad

| Operación | Tiempo Objetivo | Crítico para UX |
|-----------|-----------------|-----------------|
| Detectar selección | < 50ms | ✓ |
| Tokenizar texto | < 100ms | ✓ |
| Consultar JMDICT | < 200ms | ✓ |
| Mostrar popup | < 100ms | ✓ |
| **Total** | **< 450ms** | **✓** |

### 5.2 Optimizaciones

1. **Cache en memoria:**
   - LRU Cache para 100 entradas más recientes
   - Reduce latencia de búsqueda a ~5ms para términos repetidos

2. **Índices de base de datos:**
   - Índices en `kanji` y `reading`
   - Índice en `frequency` para priorizar resultados comunes

3. **Pre-carga:**
   - Cargar entradas más comunes al iniciar servicio
   - Warm-up de conexión a base de datos

4. **Threading:**
   - Operaciones de I/O en coroutines (Dispatchers.IO)
   - UI updates en Dispatchers.Main

---

## 6. Estructura del Proyecto

```
AndroidGodTap/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/godtap/dictionary/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── service/
│   │   │   │   │   ├── TextSelectionAccessibilityService.kt
│   │   │   │   │   └── DictionaryTileService.kt
│   │   │   │   ├── overlay/
│   │   │   │   │   ├── OverlayManager.kt
│   │   │   │   │   └── DictionaryPopupView.kt
│   │   │   │   ├── tokenizer/
│   │   │   │   │   └── JapaneseTokenizer.kt
│   │   │   │   ├── database/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── DictionaryDao.kt
│   │   │   │   │   └── DictionaryEntry.kt
│   │   │   │   ├── repository/
│   │   │   │   │   └── DictionaryRepository.kt
│   │   │   │   └── util/
│   │   │   │       ├── PermissionHelper.kt
│   │   │   │       └── JapaneseTextDetector.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   └── overlay_dictionary_popup.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   └── xml/
│   │   │   │       └── accessibility_service_config.xml
│   │   │   ├── assets/
│   │   │   │   └── jmdict.db (base de datos JMDICT)
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 7. Dependencias (build.gradle.kts)

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // Jetpack Compose (UI moderna)
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Room (Base de datos)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    
    // Optional: Kuromoji para tokenización avanzada
    // implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
}
```

---

## 8. Consideraciones de Seguridad y Privacidad

### 8.1 Privacidad del Usuario

- **No almacenar texto capturado:** Descartar texto inmediatamente después de buscar en diccionario
- **Sin conexión a internet:** Toda la funcionalidad es local (excepto descarga inicial de JMDICT)
- **Sin analytics:** No enviar datos de uso a servidores externos
- **Transparencia:** Explicar claramente en la UI qué permisos se necesitan y por qué

### 8.2 Filtrado de Contextos Sensibles

```kotlin
private fun shouldProcessText(event: AccessibilityEvent): Boolean {
    val packageName = event.packageName?.toString() ?: return false
    
    // Ignorar aplicaciones de contraseñas
    if (packageName.contains("password", ignoreCase = true)) return false
    
    // Ignorar campos de contraseña
    if (event.isPassword) return false
    
    // Ignorar teclados
    if (packageName.contains("keyboard", ignoreCase = true)) return false
    
    return true
}
```

---

## 9. Plan de Implementación del MVP

### Fase 1: Configuración Base (Día 1-2)
- [ ] Crear proyecto Android en Android Studio
- [ ] Configurar permisos en AndroidManifest
- [ ] Implementar MainActivity con UI básica
- [ ] Implementar PermissionHelper para solicitar permisos

### Fase 2: Servicio de Accesibilidad (Día 3-4)
- [ ] Crear TextSelectionAccessibilityService
- [ ] Configurar accessibility_service_config.xml
- [ ] Implementar detección de eventos de selección
- [ ] Implementar extracción de texto seleccionado
- [ ] Agregar logs para debugging

### Fase 3: Base de Datos JMDICT (Día 5-6)
- [ ] Descargar y procesar archivo JMdict XML
- [ ] Convertir a formato SQLite
- [ ] Crear schema de base de datos
- [ ] Implementar DictionaryDao con Room
- [ ] Crear DictionaryRepository con cache
- [ ] Incluir base de datos en assets/

### Fase 4: Tokenización (Día 7-8)
- [ ] Implementar JapaneseTokenizer básico
- [ ] Agregar detección de caracteres japoneses
- [ ] Implementar estrategia de búsqueda (largo a corto)
- [ ] Testing con palabras comunes

### Fase 5: Overlay Popup (Día 9-10)
- [ ] Crear OverlayManager
- [ ] Diseñar layout del popup (XML o Compose)
- [ ] Implementar lógica de posicionamiento
- [ ] Agregar animaciones de entrada/salida
- [ ] Implementar cierre automático

### Fase 6: Integración y Testing (Día 11-12)
- [ ] Conectar todos los componentes
- [ ] Testing en apps reales (Chrome, Kindle, etc.)
- [ ] Optimizar rendimiento (medir latencias)
- [ ] Ajustar UX según feedback
- [ ] Agregar notificación de estado

### Fase 7: Pulido del MVP (Día 13-14)
- [ ] Mejorar UI/UX del popup
- [ ] Agregar tutorial de primera ejecución
- [ ] Implementar Quick Settings Tile
- [ ] Testing extensivo en diferentes apps
- [ ] Documentación de usuario

---

## 10. Métricas de Éxito del MVP

### 10.1 Funcionalidad

- ✓ Usuario puede activar/desactivar el servicio fácilmente
- ✓ Detección de selección funciona en 90% de apps populares
- ✓ Popup aparece en < 500ms después de selección
- ✓ Diccionario muestra resultados correctos para palabras comunes (N5-N3)
- ✓ App no crashea durante uso normal

### 10.2 Usabilidad

- ✓ Menos de 3 pasos para activar la funcionalidad
- ✓ Popup no obstruye el texto original
- ✓ Usuario puede cerrar popup fácilmente
- ✓ App consume < 50MB de RAM en background
- ✓ Batería: < 2% de drain por hora de uso activo

---

## 11. Limitaciones Conocidas del MVP

### 11.1 Técnicas

1. **Apps no compatibles:**
   - Apps que bloquean AccessibilityService
   - Apps con texto en imágenes (OCR no incluido)
   - Apps con texto en WebView complejo

2. **Tokenización básica:**
   - No maneja todas las conjugaciones perfectamente
   - Puede fallar con expresiones idiomáticas complejas
   - Sin análisis gramatical profundo

3. **Diccionario limitado:**
   - Solo entradas comunes (para reducir tamaño)
   - Sin ejemplos de uso o frases
   - Sin audio de pronunciación

### 11.2 UX

1. **Posicionamiento del popup:**
   - Puede quedar fuera de pantalla en algunos casos
   - No se adapta perfectamente a orientación horizontal

2. **Interacción:**
   - No permite copiar traducción directamente
   - No hay historial de búsquedas
   - No hay favoritos/bookmarks

---

## 12. Próximos Pasos Post-MVP

### Features a considerar después del MVP:

1. **Tokenización avanzada:** Integrar Kuromoji o Sudachi
2. **Más idiomas:** Soporte para chino, coreano
3. **OCR:** Detectar texto en imágenes
4. **Historial:** Guardar palabras buscadas
5. **Flashcards:** Convertir palabras en tarjetas de estudio
6. **Sincronización:** Sync entre dispositivos
7. **Pronunciación:** Audio TTS para palabras
8. **Ejemplos:** Frases de ejemplo para cada entrada
9. **Configuración:** Tamaño de fuente, temas, etc.
10. **Widget:** Búsqueda rápida desde home screen

---

## 13. Recursos y Referencias

### 13.1 APIs y Documentación

- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [WindowManager Overlay](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [JMdict](http://www.edrdg.org/jmdict/j_jmdict.html)

### 13.2 Herramientas

- **Android Studio:** IDE oficial
- **JMdict Parser:** [jmdict-simplified](https://github.com/scriptin/jmdict-simplified)
- **SQLite Browser:** Para inspeccionar base de datos
- **ADB:** Para debugging del servicio de accesibilidad

### 13.3 Apps de Referencia

- **Yomiwa:** Diccionario con OCR
- **Takoboto:** Diccionario offline
- **Jisho:** Interfaz web de referencia

---

## 14. Checklist Final del MVP

### Pre-Launch

- [ ] Todos los permisos funcionan correctamente
- [ ] Servicio de accesibilidad se mantiene activo
- [ ] Popup aparece consistentemente
- [ ] Base de datos JMDICT está completa
- [ ] Testing en al menos 5 apps populares:
  - [ ] Chrome
  - [ ] Kindle
  - [ ] Twitter/X
  - [ ] Lector de PDF
  - [ ] App de noticias

### Documentación

- [ ] README.md con instrucciones de instalación
- [ ] Tutorial in-app para primera ejecución
- [ ] Política de privacidad (aunque sea simple)

### Optimización

- [ ] Latencia promedio < 500ms
- [ ] Consumo de RAM < 50MB
- [ ] Tamaño de APK < 30MB

---

## 15. Conclusión

Este documento proporciona una hoja de ruta técnica completa para desarrollar un MVP funcional de GodTap Dictionary. El enfoque está en crear una experiencia rápida, confiable y no intrusiva que permita a los usuarios aprender japonés de forma natural mientras leen en cualquier aplicación.

**Tiempo estimado de desarrollo:** 2 semanas (1 desarrollador con experiencia en Android)

**Prioridad máxima:** Velocidad y confiabilidad de la detección + popup

**Siguiente paso:** Comenzar con la Fase 1 (Configuración Base) y establecer el ambiente de desarrollo.

---

**Fecha de creación:** 30 de diciembre de 2025  
**Versión del documento:** 1.0  
**Estado:** Listo para implementación
