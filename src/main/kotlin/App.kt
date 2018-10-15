import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.WritableImage
import tornadofx.*
import java.io.File
import javax.imageio.ImageIO

var rayScene: Scene? = null

fun main(vararg args: String?) {
    val sceneFile = args[0]
    if (!sceneFile.isNullOrBlank()) {
        rayScene = parseSceneFile(File(sceneFile))
    }
    launch<RayApp>()
}

class RayApp: App(RayView::class)

class RayView: View() {
    val display = canvas(rayScene?.camera?.width ?: 800.0, rayScene?.camera?.height ?: 500.0) {
        graphicsContext2D.fillText("Loading...", 70.0, 105.0)
    }

    override val root = vbox {
        add(display)
        button("Export") {
            action {
                val image = WritableImage(display.width.toInt(), display.height.toInt())
                display.snapshot(null, image)
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", File("export.png"))

                text = "Exported to export.png"
                runAsync {
                    Thread.sleep(3000)
                } ui {
                    text = "Export"
                }
            }
        }

        runAsync {
            val scene = rayScene
            if (scene != null) {
                createRaycast(scene)
            } else {
                createRaycast(display.width.toInt(), display.height.toInt())
            }
        } ui {
            display.graphicsContext2D.drawImage(it, 0.0, 0.0)
        }
    }
}