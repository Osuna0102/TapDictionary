# Guía Rápida de Instalación - GodTap Dictionary MVP

## 🚀 Instalación en 3 pasos

### Paso 1: Abrir el proyecto en Android Studio

```bash
# Navega al directorio del proyecto
cd /Applications/XAMPP/xamppfiles/htdocs/devs/AndroidGodTap

# Abre Android Studio y selecciona:
# File > Open > Selecciona esta carpeta
```

### Paso 2: Sincronizar Gradle

Android Studio automáticamente sincronizará el proyecto. Si no lo hace:
1. Click en el icono del elefante 🐘 (Sync Project with Gradle Files)
2. Espera a que descargue las dependencias (~2-3 minutos)

### Paso 3: Conectar dispositivo y ejecutar

**Opción A: Dispositivo físico (RECOMENDADO)**
```bash
# Activa "Opciones de Desarrollador" en tu Android:
# Configuración > Acerca del teléfono > Tap 7 veces en "Número de compilación"
# Configuración > Opciones de Desarrollador > Activar "Depuración USB"

# Conecta tu teléfono por USB
adb devices  # Verifica que aparezca tu dispositivo

# En Android Studio:
# Run > Run 'app' (Shift+F10)
```

**Opción B: Emulador**
```bash
# En Android Studio:
# Tools > Device Manager > Create Device
# Selecciona un dispositivo (ej: Pixel 6)
# Selecciona Android 12 (API 31) o superior
# Finish

# Run > Run 'app'
```

---

## 📱 Configuración en el dispositivo

### 1. Primera ejecución

La app se abrirá y verás 2 pasos:

**Paso 1: Permiso de Overlay**
- Tap en "Otorgar Permiso"
- Se abrirá Configuración
- Activa el interruptor para "GodTap Dictionary"
- Vuelve a la app (botón atrás)

**Paso 2: Servicio de Accesibilidad**
- Tap en "Activar Servicio"
- Se abrirá Configuración > Accesibilidad
- Busca "GodTap Dictionary" en la lista
- Tap en él
- Activa el interruptor
- Confirma en el diálogo
- Vuelve a la app

### 2. Verificar que funciona

En la app verás:
```
✓ Permiso otorgado
✓ Servicio activo
¡Todo listo!
```

Tap en "Probar Diccionario" para ir a la pantalla de prueba.

---

## ✅ Probar la funcionalidad

### Opción 1: Usar la pantalla de prueba

1. En la app, tap en "Probar Diccionario"
2. Verás texto japonés: 食べる, 本を読む, etc.
3. **Mantén presionado** sobre una palabra
4. Aparecerán los controles de selección
5. **Suelta el dedo**
6. ¡El popup debería aparecer con la traducción!

### Opción 2: Usar Chrome

1. Abre Chrome en tu dispositivo
2. Ve a cualquier página con texto japonés (ej: wikipedia.org/wiki/日本)
3. **Mantén presionado** sobre una palabra japonesa
4. **Selecciona el texto**
5. ¡El popup debería aparecer!

### Opción 3: Usar cualquier app

Funciona en:
- 📧 Gmail (si recibes emails en japonés)
- 🐦 Twitter/X
- 📚 Apps de lectura (Kindle, Google Play Books)
- 💬 WhatsApp, Telegram
- 🌐 Cualquier navegador

---

## 🐛 Solución de Problemas

### ❌ El popup NO aparece

**Causa 1: El servicio no está activo**
```bash
# Verifica en logs:
adb logcat | grep TextSelectionService

# Deberías ver:
# TextSelectionService: Service connected
```

**Solución:**
- Ve a Configuración > Accesibilidad
- Desactiva y reactiva "GodTap Dictionary"

**Causa 2: El permiso de overlay no está otorgado**
```bash
# Verifica:
adb shell appops get com.godtap.dictionary SYSTEM_ALERT_WINDOW

# Debería decir: "allow"
```

**Solución:**
- Ve a Configuración > Apps > GodTap Dictionary
- Permisos > Mostrar sobre otras apps > Permitir

**Causa 3: La palabra no está en el diccionario**

El MVP solo tiene ~40 palabras comunes. Prueba con estas:
- 食べる (taberu = comer)
- 本 (hon = libro)
- 学校 (gakkou = escuela)
- 日本語 (nihongo = idioma japonés)
- 美味しい (oishii = delicioso)

### ❌ La app crashea al iniciar

```bash
# Limpia y recompila:
./gradlew clean
./gradlew assembleDebug

# O en Android Studio:
# Build > Clean Project
# Build > Rebuild Project
```

### ❌ Gradle no sincroniza

```bash
# Verifica tu conexión a internet (necesita descargar dependencias)

# Elimina caché de Gradle:
rm -rf ~/.gradle/caches

# En Android Studio:
# File > Invalidate Caches > Invalidate and Restart
```

---

## 📊 Verificar que está funcionando (Logs)

```bash
# Terminal 1: Ver logs del servicio
adb logcat | grep TextSelectionService

# Terminal 2: Ver logs del overlay
adb logcat | grep OverlayManager

# Terminal 3: Ver logs de búsqueda en diccionario
adb logcat | grep DictionaryRepository
```

**Salida esperada al seleccionar "食べる":**
```
TextSelectionService: Text selected: 食べる
TextSelectionService: Japanese text detected: 食べる
TextSelectionService: Processing Japanese text: 食べる
TextSelectionService: Generated 8 tokens: [食べる, 食べ, 食, べる, ...]
TextSelectionService: Found entry: 食べる (たべる) -> to eat, to consume
OverlayManager: Popup shown: 食べる (たべる) -> [verb]
to eat, to consume
```

---

## 🎯 Qué debería funcionar en el MVP

✅ **Funciona:**
- Detectar selección de texto en cualquier app
- Mostrar popup flotante
- Buscar en diccionario local (~40 palabras)
- Tokenización básica de japonés
- Auto-cerrar popup después de 10 segundos
- Cerrar popup con botón "×"

❌ **NO funciona (limitaciones del MVP):**
- OCR (texto en imágenes)
- Diccionario completo (solo palabras comunes)
- Conjugación automática avanzada
- Historial de búsquedas
- Favoritos
- Configuración de idioma

---

## 📞 Siguiente paso

Si todo funciona:
1. ✅ El servicio detecta texto seleccionado
2. ✅ El popup aparece
3. ✅ La búsqueda en diccionario funciona

**→ El MVP está completo y listo para testear la viabilidad de la idea.**

Próximos pasos sugeridos:
1. Agregar más palabras al diccionario
2. Mejorar la tokenización (Kuromoji)
3. Integrar diccionario completo (JMdict)
4. Agregar OCR para texto en imágenes
5. Mejorar UI/UX del popup

---

**¿Problemas?** Revisa los logs con `adb logcat` y busca errores.
