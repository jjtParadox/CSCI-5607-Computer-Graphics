import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.*
import kotlin.system.measureTimeMillis

// Example scene (currently broken)
fun createRaycast(w: Int, h: Int): Image {
    val camera = Camera(Point3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), 0.2, w.toDouble(), h.toDouble())
//    val sphere = Sphere(Point3d(90.0, 0.0, 0.0), 20.0, RayColor(200.0, 100.0, 100.0))
//    val smallSphere = Sphere(Point3d(77.0, 30.0, -7.0), 5.0, RayColor(100.0, 100.0, 200.0), diffuse = 0.5, spec = 1.0)
    val sphere = Sphere(Point3d(90.0, 0.0, 0.0), 20.0, Material(RayColor(200.0, 100.0, 100.0) / 255.0, RayColor(1.0, 1.0, 1.0), RayColor(1.0, 1.0, 1.0) * 3.0, 30.0, RayColor(0.0, 0.0, 0.0), 0.0))
    val scene = Scene(camera, listOf(sphere), RayColor(40.0, 40.0, 40.0) / 255.0, listOf(PointLight(Point3d(90.0, 300000.0, 0.0))), RayColor(1.0, 1.0, 1.0) * 0.15)
    return createRaycast(scene)
}

fun createRaycast(scene: Scene): Image {
    // Hardcoded jitter switch (Looks bad on spheres, so disabled)
    val jitter = false
    val w = scene.camera.width.roundToInt()
    val h = scene.camera.height.roundToInt()
    // Create an image to write to
    val img = WritableImage(w, h)
    val px = img.pixelWriter

    // Measure time of what occurs in the brackets so I can improve later
    val time = measureTimeMillis {
        for (x in 0 until w) {
            for (y in 0 until h) {
                val samples = LinkedList<RayColor>()
                // Take 9 samples per pixel
                for (i in 1..3) {
                    for (j in 1..3) {
                        val ray = if (jitter)
                            scene.constructRay((x + Math.random()) / w, (y + Math.random()) / h)
                        else
                            scene.constructRay((x + 0.25 * i) / w, (y + 0.25 * j) / h)
                        val point = scene.findIntersectionColor(ray)
                        samples.add(point)
                    }
                }
                // Set pixel position (x, y) to the average of the 9 samples
                // fold {} takes an initial value and a lambda operation to do on each element of the list
                // Here, it's starting at black and adding 1/9th of each sample to that black ("acc" is the in-progres sum)
                px.setColor(x, y, samples.fold(RayColor(0.0, 0.0, 0.0)) { acc, color -> acc + color/9.0 }.toColor())
            }
        }
    }
    println("Took $time ms")
    return img
}

data class Camera(val pos: Point3d, val towards: Vector3d, val up: Vector3d, val yFrustum: Double, val width: Double, val height: Double) {
    init {
        towards.normalize()
        up.normalize()
    }

    val dist = height/2 * 1.0/tan(yFrustum)
    val xFrustum = atan2(width/2, dist)

    // Infix function equivalent to towards.cross(up) (defined in Math.kt)
    val right = towards cross up

    init {
        right.normalize()
    }

    val centerPoint = pos + dist*towards
    val leftPoint = centerPoint - dist*tan(xFrustum)*right
    val rightPoint = centerPoint + dist*tan(xFrustum)*right
    val upPoint = centerPoint + dist*tan(yFrustum)*up
    val downPoint = centerPoint - dist*tan(yFrustum)*up

    fun constructRay(i: Double, j: Double): Ray {
        val x = leftPoint.lerp(rightPoint, i) - centerPoint
        val y = upPoint.lerp(downPoint, j) - centerPoint
        val point = x + y + centerPoint
        // Take the result of (point - pos) and normalize it
        val vector = (point - pos).apply { this.normalize() }
        return Ray(pos, vector)
    }
}

data class Ray(val pos: Point3d, val vec: Vector3d)

data class Material(var ambient: RayColor, var diffuse: RayColor, var specular: RayColor, var phongExp: Double, var trans: RayColor, var refraction: Double)

interface RayIntersector {
    fun intersect(ray: Ray): Point3d?

    fun ambientAt(point: Point3d): RayColor
    fun diffuseAt(point: Point3d): RayColor
    fun specAt(point: Point3d): RayColor
    fun phongExpAt(point: Point3d): Double

    fun normalAt(point: Point3d): Vector3d
}

data class Sphere(var pos: Point3d, var r: Double, var material: Material) : RayIntersector {
    override fun intersect(ray: Ray): Point3d? {
        val a = ray.vec.lengthSquared
        val b = 2.0*ray.vec dot (ray.pos - pos)
        val c = (ray.pos - pos).lengthSquared - r*r

        val t1 = (-b + sqrt(b*b - 4*a*c))/(2*a)
        val t2 = (-b - sqrt(b*b - 4*a*c))/(2*a)
        if ((t1 < 0 || t1.isNaN()) && (t2 < 0 || t2.isNaN()))
            return null

        val p1 = ray.pos + t1 * ray.vec
        val p2 = ray.pos + t2 * ray.vec
        return if (p1.distanceSquared(ray.pos) < p2.distanceSquared(ray.pos)) p1 else p2
    }

    override fun ambientAt(point: Point3d) = material.ambient
    override fun diffuseAt(point: Point3d) = material.diffuse
    override fun specAt(point: Point3d) = material.specular
    override fun phongExpAt(point: Point3d) = material.phongExp

    override fun normalAt(point: Point3d) = (point - pos).apply { this.normalize() }

    // Used in example scene to produce a two-toned sphere. Currently unused
    private fun colorGradientAt(point: Point3d): RayColor {
        val vec = point - pos
        return material.ambient.lerp(RayColor(1.0, 1.0, 1.0) - material.ambient, (vec.z + r) / (2*r))
    }
}

abstract class Light

data class PointLight(val pos: Point3d, val color: RayColor = RayColor(1.0, 1.0, 1.0)) : Light()

class Scene(val camera: Camera, val objects: List<RayIntersector>, val background: RayColor = RayColor(0.0, 0.0, 0.0), val lights: List<PointLight>, val ambient: RayColor) {
    fun constructRay(i: Double, j: Double) = camera.constructRay(i, j)

    fun findIntersectionColor(ray: Ray): RayColor {
        // Create map of objects to collision points
        val collisions = findIntersections(ray)
        // Get the object and collision point that is closest to the camera. If collisions is empty (collisions.minBy() returns null), return background color
        val (obj, point) = collisions.minBy { it.value.distanceSquared(camera.pos) } ?: return background
        return lightingAt(point, obj)
    }

    fun findIntersections(ray: Ray): Map<RayIntersector, Point3d> {
        val collisions = LinkedHashMap<RayIntersector, Point3d>()
        for (obj in objects) {
            val point = obj.intersect(ray) ?: continue
            collisions[obj] = point
        }
        return collisions
    }

    fun lightingAt(point: Point3d, obj: RayIntersector): RayColor {
        // Sum up the lights
        return lights.fold(RayColor(0.0, 0.0, 0.0)) { acc, light ->
            val lightVec = (light.pos - point)
            val lightDist = lightVec.lengthSquared
            lightVec.normalize()
            val normal = obj.normalAt(point)
            if (lightVec dot normal < 0)
                return@fold acc // If dot is negative, don't change the running sum (return acc to the fold lambda unchanged)

            val shadowRay = Ray(point + 1e-10 * normal, lightVec)
            if (findIntersections(shadowRay).any { it.value.distanceSquared(light.pos) <= lightDist })
                return@fold acc // If point is in shadow, don't change the running sum

            val lightRefl = lightVec - 2 * (lightVec dot normal) * normal
            lightRefl.negate()
            val viewVec = (camera.pos - point).apply { this.normalize() }
            val lighting = obj.specAt(point) * light.color * 1.0/lightDist * (viewVec dot lightRefl).pow(obj.phongExpAt(point)) + obj.diffuseAt(point)  * light.color * 1.0/lightDist * (lightVec dot normal)
            return@fold acc + lighting // Add lighting to the running sum
        } + ambient * obj.ambientAt(point) // Add ambient to the sum of lights
    }
}
