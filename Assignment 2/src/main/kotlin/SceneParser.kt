import java.io.File
import java.lang.Exception
import kotlin.reflect.KMutableProperty0

// Parse the given file and return a Scene
fun parseSceneFile(file: File): Scene {
    val reader = file.bufferedReader()

    // Simple data storage object
    val blueprint = SceneFileBlueprint()
    // Each geometry gets the material defined earlier in the file, so store that material here
    var currentMaterial = SceneFileMaterial()

    reader.forEachLine {
        // Split line on spaces
        val params = it.split(' ').filter { i -> i.isNotBlank() }
        // Helper function to get param i as a double
        val get = { i: Int -> params[i].toDouble() }
        try {
            if (params.isNotEmpty())
                // Switch on the first parameter
                when (params[0]) {
                    // Same as "if params[0] is "camera", apply the following to the blueprint camera's fields"
                    "camera" -> blueprint.camera.apply {
                        px = get(1)
                        py = get(2)
                        pz = get(3)

                        dx = get(4)
                        dy = get(5)
                        dz = get(6)

                        ux = get(7)
                        uy = get(8)
                        uz = get(9)

                        ha = get(10)
                    }
                    "film_resolution" -> blueprint.camera.apply {
                        w = params[1].toInt()
                        h = params[2].toInt()
                    }
                    "output_image" -> blueprint.camera.apply {
                        filename = params[1]
                    }
                    "sphere" -> blueprint.geometry.add(SceneFileSphere().apply {
                        x = get(1)
                        y = get(2)
                        z = get(3)
                        r = get(4)

                        material = currentMaterial
                    })

                    // Here I figured out how to pass references around, so I made a helper function to write to a
                    // list of fields from a lambda
                    "background" -> blueprint.background.apply {
                        toProps(::r, ::g, ::b) { i -> params[i + 1].toDouble() }
                    }
                    "material" -> currentMaterial = SceneFileMaterial().apply {
                        toProps(::ar, ::ag, ::ab, ::dr, ::dg, ::db, ::sr, ::sg, ::sb, ::ns, ::tr, ::tg, ::tb, ::ior) { i -> params[i + 1].toDouble() }
                    }
                    "directional_light" -> blueprint.lights.add(SceneFileDirLight().apply {
                        toProps(::r, ::g, ::b, ::x, ::y, ::z) { i -> params[i + 1].toDouble() }
                    })
                    "point_light" -> blueprint.lights.add(SceneFilePointLight().apply {
                        toProps(::r, ::g, ::b, ::x, ::y, ::z) { i -> params[i + 1].toDouble() }
                    })
                    "spot_light" -> blueprint.lights.add(SceneFileSpotLight().apply {
                        toProps(::r, ::g, ::b, ::px, ::py, ::pz, ::dx, ::dy, ::dz, ::angle1, ::angle2) { i -> params[i + 1].toDouble() }
                    })
                    "ambient_light" -> blueprint.ambientLight.apply {
                        toProps(::r, ::g, ::b) { i -> params[i + 1].toDouble() }
                    }
                    "max_depth" -> blueprint.maxDepth = params[1].toInt()
                }
        } catch (e: Exception) {
            System.err.println("Unable to parse line $it")
            throw e
        }
    }
    return blueprint.toScene()
}

// Helper function to write to a list of fields from a lambda
private fun <T> toProps(vararg props: KMutableProperty0<T>?, getter: (Int) -> T) {
    props.forEachIndexed { index, prop ->
        prop?.set(getter(index))
    }
}

// Simple data storage object to hold everything from the scene file
class SceneFileBlueprint {
    var camera = SceneFileCamera()
    var geometry = mutableListOf<SceneFileShape>()
    var background = SceneFileBackground()
    var lights = mutableListOf<SceneFileLight>()
    var ambientLight = SceneFileAmbientLight()
    var maxDepth = 5

    // Convert data storage object to true Scene
    fun toScene(): Scene {
        // run {} allows camera's fields to be referenced without doing camera.px, camera.py, etc
        val trueCamera = camera.run { Camera(Point3d(px, py, pz), Vector3d(dx, dy, dz), Vector3d(ux, uy, uz), Math.toRadians(ha), w.toDouble(), h.toDouble()) }
        val trueGeometry = geometry.map {
            it.run {
                when (this) {
                    is SceneFileSphere -> Sphere(Point3d(x, y, z), r, material.run {
                        Material(RayColor(ar, ag, ab), RayColor(dr, dg, db), RayColor(sr, sg, sb), ns, RayColor(tr, tg, tb), ior)
                    })
                    // Other geometry is not implemented, so throw an error
                    else -> TODO("Not implemented")
                }
            }
        }
        val trueBackground = background.run { RayColor(r, g, b) }
        val trueLights = lights.map {
            it.run {
                when (this) {
                    is SceneFilePointLight -> PointLight(Point3d(x, y, z), RayColor(r, g, b))
                    else -> TODO("Not implemented")
                }
            }
        }
        val trueAmbient = ambientLight.run { RayColor(r, g, b) }
        return Scene(trueCamera, trueGeometry, trueBackground, trueLights, trueAmbient)
    }
}

// Rest of the data storage objects for the blueprint to use

class SceneFileCamera {
    var px = 0.0
    var py = 0.0
    var pz = 0.0

    var dx = 0.0
    var dy = 0.0
    var dz = 1.0

    var ux = 0.0
    var uy = 1.0
    var uz = 0.0

    var ha = 45.0

    var w = 640
    var h = 480

    var filename = "raytraced.bmp"
}

open class SceneFileShape {
    var material = SceneFileMaterial()
}

class SceneFileSphere : SceneFileShape() {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var r = 1.0
}

class SceneFileBackground {
    var r = 0.0
    var g = 0.0
    var b = 0.0
}

class SceneFileMaterial {
    var ar = 0.0
    var ag = 0.0
    var ab = 0.0

    var dr = 1.0
    var dg = 1.0
    var db = 1.0

    var sr = 0.0
    var sg = 0.0
    var sb = 0.0
    var ns = 5.0

    var tr = 0.0
    var tg = 0.0
    var tb = 0.0
    var ior = 1.0
}

open class SceneFileLight {
    var r = 0.0
    var g = 0.0
    var b = 0.0
}

class SceneFileDirLight : SceneFileLight() {
    var x = 0.0
    var y = 0.0
    var z = 0.0
}

class SceneFilePointLight : SceneFileLight() {
    var x = 0.0
    var y = 0.0
    var z = 0.0
}

class SceneFileSpotLight : SceneFileLight() {
    var px = 0.0
    var py = 0.0
    var pz = 0.0

    var dx = 0.0
    var dy = 0.0
    var dz = 0.0

    var angle1 = 0.0
    var angle2 = 0.0
}

class SceneFileAmbientLight : SceneFileLight()
