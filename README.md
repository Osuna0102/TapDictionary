# GodTap Dictionary - Android App

Una aplicación Android que permite traducir palabras japonesas seleccionadas en cualquier aplicación del dispositivo.

## 🎉 Nuevas Características (Enero 2026)

### ✨ Controles por Gestos
- **2 dedos deslizar derecha** → Activar modo OCR
- **3 dedos tap** → Activar/desactivar servicio
- **3 dedos deslizar derecha** → Activar/desactivar subrayado

### 📷 Reconocimiento de Texto (OCR)
- Selecciona cualquier área de la pantalla
- Reconocimiento automático de texto con ML Kit
- Traducción inteligente: diccionario + API
- Funciona con texto en imágenes, capturas, etc.

### 🔧 Quick Settings Tile Arreglado
- El botón "Dictionary" en configuración rápida ahora abre la app

📖 **Documentación completa**: Ver [FEATURE_GESTURES_OCR.md](FEATURE_GESTURES_OCR.md)  
🧪 **Guía de pruebas**: Ver [TESTING_GUIDE.md](TESTING_GUIDE.md)

---

## Características del MVP

- ✅ Detección de texto seleccionado en cualquier app (usando AccessibilityService)
- ✅ Popup flotante con traducción automática
- ✅ Diccionario japonés-español integrado (palabras comunes N5)
- ✅ Tokenización inteligente de texto japonés
- ✅ Base de datos local (no requiere internet después de instalación)
- ✅ **NUEVO**: Control por gestos multi-dedo
- ✅ **NUEVO**: OCR con selección de área
- ✅ **NUEVO**: Integración ML Kit Text Recognition

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio Hedgehog o superior
- Gradle 8.2+

## Instalación y Configuración

### 1. Clonar el proyecto (o abrir en Android Studio)

```bash
cd AndroidGodTap
```

### 2. Abrir en Android Studio

- Abre Android Studio
- File > Open > Selecciona la carpeta `AndroidGodTap`
- Espera a que Gradle sincronice

### 3. Compilar y ejecutar

```bash
# Desde Android Studio: Run > Run 'app'
# O desde terminal:
./gradlew assembleDebug
./gradlew installDebug
```

## Uso de la App

### Primera vez:

1. **Abrir la app** → Verás la pantalla de configuración
2. **Otorgar permiso de Overlay** → Tap en "Otorgar Permiso" y activa el permiso
3. **Activar Servicio de Accesibilidad**:
   - Tap en "Activar Servicio"
   - En Configuración > Accesibilidad
   - Busca "GodTap Dictionary"
   - Actívalo
4. **¡Listo!** → Verás el mensaje "Todo listo"

### Usar el diccionario:

1. Abre cualquier app (Chrome, Kindle, Twitter, etc.)
2. **Selecciona texto japonés** (mantén presionado y selecciona)
3. El diccionario aparecerá automáticamente en un popup flotante
4. Tap en "×" para cerrar o espera 10 segundos

## Estructura del Proyecto

```
app/src/main/java/com/godtap/dictionary/
├── DictionaryApp.kt                    # Application class
├── MainActivity.kt                     # Pantalla principal con permisos
├── TestActivity.kt                     # Pantalla de prueba con texto japonés
├── service/
│   ├── TextSelectionAccessibilityService.kt  # Servicio de accesibilidad (CORE)
│   └── DictionaryTileService.kt              # Quick Settings Tile
├── overlay/
│   └── OverlayManager.kt                     # Gestión del popup flotante
├── database/
│   ├── DictionaryEntry.kt                    # Modelo de datos
│   ├── DictionaryDao.kt                      # DAO de Room
│   └── AppDatabase.kt                        # Base de datos Room
├── repository/
│   └── DictionaryRepository.kt               # Lógica de búsqueda
├── tokenizer/
│   └── JapaneseTokenizer.kt                  # Tokenizador de texto japonés
└── util/
    ├── JapaneseTextDetector.kt               # Detección de caracteres japoneses
    └── PermissionHelper.kt                   # Helper de permisos
```

## Cómo Funciona

### 1. AccessibilityService

El `TextSelectionAccessibilityService` monitorea eventos del sistema:
- `TYPE_VIEW_TEXT_SELECTION_CHANGED` → Detecta cuando el usuario selecciona texto
- Filtra contextos sensibles (contraseñas, teclados, etc.)
- Extrae el texto seleccionado

### 2. Tokenización

El `JapaneseTokenizer` convierte el texto en tokens:
```
"食べました" → ["食べました", "食べる", "食べ", "食", "べました", "べる", ...]
```
Los tokens se ordenan de más largo a más corto para buscar coincidencias exactas primero.

### 3. Búsqueda en Diccionario

El `DictionaryRepository` busca en la base de datos:
1. Busca por kanji
2. Si no encuentra, busca por lectura (hiragana)
3. Si no encuentra, busca por prefijo
4. Usa LRU cache para velocidad

### 4. Popup Overlay

El `OverlayManager` muestra el resultado:
- Usa `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- Posicionado en el centro (puede ajustarse)
- Auto-cierre después de 10 segundos
- Click en "×" para cerrar manualmente

## Palabras Incluidas (MVP)

Base de datos con ~40 palabras comunes JLPT N5:

**Verbos:** 食べる, 飲む, 見る, 聞く, 読む, 書く, 話す, 行く, 来る, する, 勉強する

**Sustantivos:** 本, 学校, 先生, 学生, 日本, 日本語, 英語, 時間, 友達, 家, 水, 寿司

**Adjetivos:** 美味しい, 大きい, 小さい, 新しい, 古い, 良い, 悪い

**Otros:** 私, 今, 毎日, 今日, 明日, 昨日

## Limitaciones del MVP

### No incluye:
- ❌ OCR (texto en imágenes)
- ❌ Conjugación automática avanzada
- ❌ Diccionario completo (solo ~40 palabras)
- ❌ Audio de pronunciación
- ❌ Ejemplos de uso
- ❌ Historial de búsquedas
- ❌ Favoritos

### Apps que podrían no funcionar:
- Apps que bloquean AccessibilityService
- Juegos con texto en imágenes
- Apps con protección DRM estricta

## Debugging

### Ver logs:

```bash
# Filtrar logs del servicio de accesibilidad
adb logcat | grep TextSelectionService

# Ver todos los logs de la app
adb logcat | grep "com.godtap.dictionary"
```

### Probar permisos:

```bash
# Verificar si el servicio de accesibilidad está activo
adb shell settings get secure enabled_accessibility_services

# Verificar permiso de overlay
adb shell appops get com.godtap.dictionary SYSTEM_ALERT_WINDOW
```

## Próximos Pasos (Post-MVP)

1. **Integrar Kuromoji** → Tokenización profesional
2. **Diccionario completo** → Importar JMdict completo (~200k entradas)
3. **OCR** → Detectar texto en imágenes (ML Kit)
4. **Conjugación automática** → Reducir verbos a forma base
5. **UI mejorada** → Animaciones, temas, configuración
6. **Historial** → Guardar palabras buscadas
7. **Flashcards** → Convertir palabras en tarjetas de estudio
8. **Multi-idioma** → Soporte para chino, coreano

## Troubleshooting

### El servicio no detecta texto seleccionado

1. Verifica que el servicio de accesibilidad esté activo:
   - Configuración > Accesibilidad > GodTap Dictionary > ON

2. Revisa los logs:
   ```bash
   adb logcat | grep TextSelectionService
   ```

3. Reinicia el servicio:
   - Desactiva y reactiva en Configuración > Accesibilidad

### El popup no aparece

1. Verifica el permiso de overlay:
   - Configuración > Apps > GodTap Dictionary > Permisos > Mostrar sobre otras apps

2. Verifica logs:
   ```bash
   adb logcat | grep OverlayManager
   ```

### La app crashea al iniciar

1. Limpia y recompila:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. Verifica que Room esté generando código:
   ```bash
   ./gradlew kaptDebugKotlin
   ```

## Contribuir

Este es un MVP experimental. Para contribuir:

1. Fork el proyecto
2. Crea una rama feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -am 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Crea un Pull Request

## Licencia

Este proyecto es de código abierto para fines educativos.

## Créditos

- Diccionario: Basado en [JMdict](http://www.edrdg.org/jmdict/j_jmdict.html)
- Inspiración: Yomiwa, Takoboto, Jisho.org

---

**Fecha de creación:** 30 de diciembre de 2025  
**Versión:** 1.0.0 (MVP)  
**Estado:** Funcional para testing básico
