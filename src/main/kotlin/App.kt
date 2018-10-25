import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.WritableImage
import tornadofx.*
import java.io.File
import javax.imageio.ImageIO

var rayScene: Scene? = null

fun main(vararg args: String?) {
    // Parse scene file to get width and height before launching GUI
    val sceneFile = if (args.isNotEmpty()) args[0] else null
    if (!sceneFile.isNullOrBlank()) {
        rayScene = parseSceneFile(File(sceneFile)) // in SceneParser.kt
    } else {
        println("Error: scene file not given. Exiting.")
        return
    }

    launch<RayApp>()
}

class RayApp: App(RayView::class)

class RayView: View() {
    // Where stuff gets drawn. Creates canvas with size from scene file (default 800x500 if no file provided) and adds "Loading" text
    val display = canvas(rayScene?.camera?.width ?: 800.0, rayScene?.camera?.height ?: 500.0) {
        graphicsContext2D.fillText("Loading...", 70.0, 105.0)
    }

    val renderStatus: TaskStatus by inject() // Attaches this value to the first task run (below)

    // Creates view structure with canvas and export button
    override val root = vbox {
        add(display)
        hbox {
            button("Export") {
                action {
                    // Export PNG from what's shown on the canvas
                    val image = WritableImage(display.width.toInt(), display.height.toInt())
                    display.snapshot(null, image)
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", File("export.png"))

                    // Show confirmation text for 3 seconds
                    text = "Exported to export.png"
                    runAsync {
                        Thread.sleep(3000)
                    } ui {
                        text = "Export"
                    }
                }
            }
            progressbar(renderStatus.progress) {
                visibleWhen { renderStatus.running }
                paddingAll = 4.0
                minWidth = display.width / 3.0
            }
            paddingAll = 4.0
        }

        // Render scene in background
        runAsync(true) {
            val scene = rayScene
            updateProgress(0.0, display.width * display.height)
            if (scene != null) {
                createRaycast(scene, this) // in RayTracer.kt
            } else {
                createRaycast(display.width.toInt(), display.height.toInt(), this) // in RayTracer.kt
            }
        } ui {
            // Update canvas with returned image (stored in 'it')
            display.graphicsContext2D.drawImage(it, 0.0, 0.0)
        }
    }
}