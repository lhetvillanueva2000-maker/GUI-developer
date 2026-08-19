# Extending the designer

## Adding a component

Three edits, in this order.

### 1. Declare it in the catalog

`sharedCore/.../catalog/ElementCatalog.kt`:

```kotlin
const val BUTTON_STEPPER = "button.stepper"

ElementDefinition(
    typeId = BUTTON_STEPPER,
    displayName = "Stepper",
    category = ElementCategory.CONTROLS,
    defaultSize = IntSize(80, 20),
    minSize = IntSize(40, 12),
    interactive = true,
    glyph = "±",
    description = "Increment / decrement control.",
    properties = listOf(
        intProp("value", "Value", 0, min = 0, max = 999, group = PropGroup.CONTENT),
        intProp("step", "Step", 1, min = 1, max = 100),
        enabledProp,
        colorProp("background", "Background", 0xFF6C6C6C),
    ),
)
```

That is already enough for the palette, the property inspector, serialization,
undo/redo, validation and the code generators to handle it. `interactive = true`
also gives it the full set of interaction states.

Property builders live in `PropertySpec.kt`: `textProp`, `intProp`, `floatProp`,
`boolProp`, `colorProp`, `enumProp`, `textureProp`, `stringListProp`. Pass
`editions = JAVA_ONLY` / `BEDROCK_ONLY` for anything edition-specific - the
validator will then explain that a value only applies to the other edition
instead of silently dropping it.

### 2. Draw it in each skin

`styles/java/src/.../JavaEditionSkin.kt` and
`styles/bedrock/src/.../BedrockEditionSkin.kt` each have a `when` on the element
type. Add a branch and a private draw function to whichever editions support it:

```kotlin
ElementCatalog.BUTTON_STEPPER -> drawStepper(context)

private fun DrawScope.drawStepper(ctx: ElementRenderContext) {
    drawButtonSurface(ctx, ctx.rect, enabled = ctx.props.bool("enabled", true))
    drawCenteredLabel(ctx, ctx.rect, ctx.props.int("value", 0).toString(), enabled = true)
}
```

Use the primitives in `styles/src/commonMain/.../render/PixelDraw.kt`
(`fillRect`, `strokeRect`, `bevelBox`, `pixelRoundRect`, `pixelCircle`,
`nineSlice`, `drawImageFitted`, `drawShadowedText`, `hatch`, `checkerboard`).
They all snap to whole device pixels.

Colours must come from the skin's own palette or from the element's properties -
never hard-code one. That is what keeps the two editions independent.

### 3. Map it in the exporters

* **Java** – `JavaEditionExporter.isWidget` decides whether the element becomes
  a real vanilla widget in `init()` or a `render()` draw call. The layout JSON
  and the language file need no changes.
* **Bedrock** – `BedrockEditionExporter.controlType` maps it to the closest
  JSON-UI control, and `appendTypeSpecifics` emits its properties.

Both fall back sensibly, so a new widget exports as a generic panel until you
get to this step.

### 4. Add a test

`CatalogTest` already checks that every definition has usable defaults and that
each default value matches its own spec, so a malformed definition fails the
build without you writing anything. Add a case to `ExportTest` if the widget
needs specific export output.

---

## Adding a template

Templates are built in code rather than shipped as data so they cannot drift
out of sync with the catalog - if a property is renamed, the templates stop
compiling.

Add a factory and a registry entry in
`sharedCore/.../templates/BuiltInTemplates.kt`, then run:

```bash
./gradlew exportTemplates
```

That writes `templates/<id>.mcgui`, regenerates `templates/sample-output/`, and
fails if the new template has any validation error. CI runs the same task and
additionally fails if the committed files are out of date.

---

## Retheming an edition

Everything a skin uses lives in its own folder:

```
styles/java/src/.../JavaPalette.kt         all colours and metrics
styles/java/src/.../JavaEditionSkin.kt     the drawing
styles/java/assets/README.md               palette reference
```

`SkinPalette` also carries the *editor chrome* colours, so changing a palette
re-tints the whole application in that edition, not just the canvas.

The only file both editions share is `PixelDraw.kt`. Nothing in `styles/java`
may reference `styles/bedrock` or vice versa.

---

## Adding a code-generation target

1. Add a `CodeTarget` entry in
   `exporters/.../CodeGenerator.kt` with its language, extension and - if it
   only makes sense for one edition - its `edition`.
2. Add a branch to `CodeGenerator.generate`.
3. Write the generator. Work from `project.absoluteBounds()` so the output is
   flat; `ExportUtil` has helpers for escaping, colour conversion (`cssRgba`,
   `bedrockColor`, `javaColorLiteral`) and identifier casing.

Both front-ends pick the target list up from `CodeGenerator.targetsFor`, so the
Code tab gains the new option with no UI change.

---

## Running things

```bash
./gradlew allTests               # every unit test
./gradlew validateProjects       # regenerate + validate the bundled templates
./gradlew generateIcons          # re-render the app icon for every platform
./gradlew :desktopApp:run        # run the desktop editor
./gradlew :androidApp:installDebug
```

Pass `-PskipAndroid=true` to build the desktop side on a machine with no
Android SDK.
