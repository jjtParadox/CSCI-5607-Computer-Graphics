import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import tornadofx.FXTask
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashMap
import kotlin.math.*
import kotlin.system.measureTimeMillis

val sceneProgress = AtomicInteger(0)
val reportedProgress = AtomicInteger(0)

// Example scene (currently broken)
fun createRaycast(w: Int, h: Int, task: FXTask<*>? = null): Image {
    val camera = Camera(Point3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), 0.2, w.toDouble(), h.toDouble())
//    val sphere = Sphere(Point3d(90.0, 0.0, 0.0), 20.0, RayColor(200.0, 100.0, 100.0))
//    val smallSphere = Sphere(Point3d(77.0, 30.0, -7.0), 5.0, RayColor(100.0, 100.0, 200.0), diffuse = 0.5, spec = 1.0)
    val sphere = Sphere(Point3d(90.0, 0.0, 0.0), 20.0, Material(RayColor(200.0, 100.0, 100.0) / 255.0, RayColor(1.0, 1.0, 1.0), RayColor(1.0, 1.0, 1.0) * 3.0, 30.0, RayColor(0.0, 0.0, 0.0), 0.0))
    val scene = Scene(camera, listOf(sphere), RayColor(40.0, 40.0, 40.0) / 255.0, listOf(PointLight(Point3d(90.0, 300000.0, 0.0))), RayColor(1.0, 1.0, 1.0) * 0.15, 0)
    return createRaycast(scene, task)
}

fun createRaycast(scene: Scene, task: FXTask<*>? = null): Image {
    // Hardcoded jitter switch (Looks bad on spheres, so disabled)
    val jitter = false
    val adaptive = true
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
                        val ray = if (jitter) {
                            scene.constructRay((x + Math.random()) / w, (y + Math.random()) / h)
                        } else {
                            if (adaptive && (i == 2 || j == 2)) continue
                            scene.constructRay((x + 0.25 * i) / w, (y + 0.25 * j) / h)
                        }
                        val point = scene.findIntersectionColor(ray)
                        samples.add(point)
                    }
                }
                var size = samples.size.toDouble()
                // Get the average of the X samples
                // fold {} takes an initial value and a lambda operation to do on each element of the list
                // Here, it's starting at black and adding 1/Xth of each sample to that black ("acc" is the in-progress sum)
                var sample = samples.fold(RayColor(0.0, 0.0, 0.0)) { acc, color -> acc + color/size }
                if (!jitter && adaptive && samples.any { (it - sample).run {
                            val sum = abs(r) + abs(g) + abs(b)
                            sum > 0.01
                        } }) {
                    for (i in 1..3) {
                        for (j in 1..3) {
                            if (i == 2 || j == 2) {
                                val ray = scene.constructRay((x + 0.25 * i) / w, (y + 0.25 * j) / h)
                                val point = scene.findIntersectionColor(ray)
                                samples.add(point)
                            }
                        }
                    }
                    size = samples.size.toDouble()
                    sample = samples.fold(RayColor(0.0, 0.0, 0.0)) { acc, color -> acc + color/size }
                }
                px.setColor(x, y, sample.toColor())

                val progress = sceneProgress.incrementAndGet()
                if (task != null) {
                    if (progress - reportedProgress.get() > 100) {
                        task.updateProgress(progress.toLong(), (w * h).toLong())
                        reportedProgress.set(progress)
                    }
                }
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
        val x = rightPoint.lerp(leftPoint, i) - centerPoint
        val y = upPoint.lerp(downPoint, j) - centerPoint
        val point = x + y + centerPoint
        // Take the result of (point - pos) and normalize it
        val vector = (point - pos).apply { this.normalize() }
        return Ray(pos, vector)
    }
}

data class Ray(val pos: Point3d, val vec: Vector3d, val depth: Int = 0)

data class Material(var ambient: RayColor, var diffuse: RayColor, var specular: RayColor, var phongExp: Double, var trans: RayColor, var refraction: Double)

interface RayIntersector {
    var material: Material

    fun intersect(ray: Ray): Point3d?

    fun ambientAt(point: Point3d): RayColor
    fun diffuseAt(point: Point3d): RayColor
    fun specAt(point: Point3d): RayColor
    fun transAt(point: Point3d): RayColor
    fun phongExpAt(point: Point3d): Double

    fun normalAt(point: Point3d): Vector3d
}

data class Sphere(var pos: Point3d, var r: Double, override var material: Material) : RayIntersector {
    override fun intersect(ray: Ray): Point3d? {
        val a = ray.vec.lengthSquared
        val b = 2.0*ray.vec dot (ray.pos - pos) // Infix function defined in Math.kt
        val c = (ray.pos - pos).lengthSquared - r*r

        val t1 = (-b + sqrt(b*b - 4*a*c))/(2*a)
        val t2 = (-b - sqrt(b*b - 4*a*c))/(2*a)
        if ((t1 < 0 || t1.isNaN()) && (t2 < 0 || t2.isNaN()))
            return null

        val p1 = ray.pos + t1 * ray.vec
        val p2 = ray.pos + t2 * ray.vec
        return when {
            t1 < 0 -> p2
            t2 < 0 -> p1

            p1.distanceSquared(ray.pos) < p2.distanceSquared(ray.pos) -> p1
            else -> p2
        }
    }

    override fun ambientAt(point: Point3d) = material.ambient
    override fun diffuseAt(point: Point3d) = material.diffuse
    override fun specAt(point: Point3d) = material.specular
    override fun transAt(point: Point3d) = material.trans
    override fun phongExpAt(point: Point3d) = material.phongExp

    override fun normalAt(point: Point3d) = (point - pos).apply { this.normalize() }

    // Used in example scene to produce a two-toned sphere. Currently unused
    private fun colorGradientAt(point: Point3d): RayColor {
        val vec = point - pos
        return material.ambient.lerp(RayColor(1.0, 1.0, 1.0) - material.ambient, (vec.z + r) / (2*r))
    }
}

data class Triangle(val p1: Point3d, val p2: Point3d, val p3: Point3d, override var material: Material) : RayIntersector {
    val vecA = p2 - p1
    val vecB = p3 - p1
    val normalA = (vecA cross vecB).apply { this.normalize() }
    val normalB = normalA.copy().apply { this.negate() }

    override fun intersect(ray: Ray): Point3d? {
        val normal = if (normalA dot ray.vec > 0) normalA else normalB
        val t = ((p1 - ray.pos) dot normal) / (normal dot ray.vec)
        if (t.isNaN() || t < 0)
            return null
        val pos = ray.pos + t * ray.vec
        val vec = pos - p1
        var a = (vecB cross vec) dot (vecB cross vecA)
        var b = (vecA cross vec) dot (vecA cross vecB)
        if (a < 0 || b < 0)
            return null
        val denom = (vecA cross vecB).length
        a = (vecB cross vec).length / denom
        b = (vecA cross vec).length / denom
        if (a + b > 1)
            return null
        return pos
    }

    override fun normalAt(point: Point3d) = normalA.copy()

    override fun ambientAt(point: Point3d) = material.ambient
    override fun diffuseAt(point: Point3d) = material.diffuse
    override fun specAt(point: Point3d) = material.specular
    override fun transAt(point: Point3d) = material.trans
    override fun phongExpAt(point: Point3d) = material.phongExp
}

data class NormTriangle(val p1: Point3d, val p2: Point3d, val p3: Point3d, val n1: Vector3d, val n2: Vector3d, val n3: Vector3d, override var material: Material) : RayIntersector {
    init {
        n1.normalize()
        n2.normalize()
        n3.normalize()
    }
    val vecA = p2 - p1
    val vecB = p3 - p1
    val planarNormal = (vecA cross vecB).apply { this.normalize() }

//    val cache = ConcurrentHashMap<Point3d, Pair<Double, Double>>()

    fun alphaBetaForPoint(point: Point3d): Pair<Double, Double>? {
        val vec = point - p1
        var a = (vecB cross vec) dot (vecB cross vecA)
        var b = (vecA cross vec) dot (vecA cross vecB)
        if (a < 0 || b < 0)
            return null
        val denom = (vecA cross vecB).length
        a = (vecB cross vec).length / denom
        b = (vecA cross vec).length / denom
        if (a + b > 1)
            return null
//        cache[point] = a to b
        return a to b
    }

    override fun intersect(ray: Ray): Point3d? {
        val t = ((p1 - ray.pos) dot planarNormal) / (planarNormal dot ray.vec)
        if (t.isNaN() || t < 0)
            return null
        val pos = ray.pos + t * ray.vec
        alphaBetaForPoint(pos) ?: return null
        return pos
    }

    override fun normalAt(point: Point3d): Vector3d {
//        val (b, c) = cache[point] ?: alphaBetaForPoint(point) ?: error("Asked for normal outside of triangle")
        val (b, c) = alphaBetaForPoint(point) ?: error("Asked for normal outside of triangle")
        val a = 1.0 - c - b
        val vector = a * n1 + b * n2 + c * n3
        vector.normalize()
//        cache.remove(point)
        return vector
    }

    override fun ambientAt(point: Point3d) = material.ambient
    override fun diffuseAt(point: Point3d) = material.diffuse
    override fun specAt(point: Point3d) = material.specular
    override fun transAt(point: Point3d) = material.trans
    override fun phongExpAt(point: Point3d) = material.phongExp
}

abstract class Light {
    abstract val color: RayColor
    abstract fun vectorTo(pos: Point3d): Vector3d
}

abstract class PosLight : Light() {
    abstract val pos: Point3d
    override fun vectorTo(pos: Point3d) = (this.pos - pos)
}

data class PointLight(override val pos: Point3d, override val color: RayColor = RayColor(1.0, 1.0, 1.0)) : PosLight()

data class SpotLight(override val pos: Point3d, val dir: Vector3d, val innerAngle: Double, val outerAngle: Double, override val color: RayColor) : PosLight() {
    init {
        dir.normalize()
    }
}

data class DirLight(val dir: Vector3d, override val color: RayColor) : Light() {
    init {
        dir.normalize()
    }
    override fun vectorTo(pos: Point3d) = dir.copy().apply { this.negate() }
}

class Scene(val camera: Camera, val objects: List<RayIntersector>, val background: RayColor = RayColor(0.0, 0.0, 0.0), val lights: List<Light>, val ambient: RayColor, val maxDepth: Int) {
    fun constructRay(i: Double, j: Double) = camera.constructRay(i, j)

    fun findIntersectionColor(ray: Ray): RayColor {
        // Create map of objects to collision points
        val collisions = findIntersections(ray)
        // Get the object and collision point that is closest to the camera. If collisions is empty (collisions.minBy() returns null), return background color
        val (obj, point) = collisions.minBy { it.value.distanceSquared(ray.pos) } ?: return background

        // Get reflected and refracted colors if we have reflections left
        var bounce = RayColor(0.0, 0.0, 0.0)
        if (ray.depth < maxDepth) {
            val normal = obj.normalAt(point)
            var adjVec = normal
            var rayDot = ray.vec dot normal
            if (obj is Triangle && rayDot > 0) {
                normal.negate()
                rayDot = -rayDot
            }
            if (obj is NormTriangle) {
                adjVec = obj.planarNormal
                if (adjVec dot normal < 0)
                    adjVec.negate()
            }

            // Calculate reflection vector and get intersection
            var reflection = RayColor(0.0, 0.0, 0.0)
            if (rayDot < 0) {
                reflection = obj.specAt(point)
                if (reflection != RayColor(0.0, 0.0, 0.0)) {
                    val reflVec = ray.vec - 2 * rayDot * normal
                    reflection = findIntersectionColor(Ray(point + 1e-10 * adjVec, reflVec, ray.depth + 1)) * reflection
                }
            }

            // Calculate refraction vector
            var refraction = obj.transAt(point)
            if (refraction != RayColor(0.0, 0.0, 0.0)) {
                val refractionAmnt: Double
                val refractionPoint: Point3d
                if (rayDot < 0) {
                    refractionPoint = point - 1e-10 * adjVec
                    refractionAmnt = 1.0 / obj.material.refraction
                } else {
                    refractionPoint = point + 1e-10 * adjVec
                    refractionAmnt = obj.material.refraction
                    // Snell's law requires the normal to be pointing towards the light source, so invert the dot product and the normal
                    rayDot = -rayDot
                    normal.negate()
                }
                val refracVec = if (refractionAmnt != 1.0) refractionAmnt * ray.vec + (-refractionAmnt * rayDot - sqrt(1 - refractionAmnt.pow(2) * (1 - rayDot.pow(2)))) * normal else ray.vec.copy()
                refraction = findIntersectionColor(Ray(refractionPoint, refracVec, ray.depth + 1)) * refraction
            }

            bounce = reflection + refraction
        }
        return lightingAt(point, obj) + bounce
    }

    fun findIntersections(ray: Ray): Map<RayIntersector, Point3d> {
        val collisions = LinkedHashMap<RayIntersector, Point3d>()
        for (obj in objects) {
            val point = obj.intersect(ray) ?: continue // If obj.intersect(ray) returns null, continue to the next obj
            collisions[obj] = point
        }
        return collisions
    }

    fun lightingAt(point: Point3d, obj: RayIntersector): RayColor {
        // Sum up the lights
        return lights.fold(RayColor(0.0, 0.0, 0.0)) { acc, light ->
            val lightVec = light.vectorTo(point)
            val lightDist = lightVec.lengthSquared
            lightVec.normalize()
            val normal = obj.normalAt(point)
            if (lightVec dot normal < 0) {
                if (obj !is Triangle)
                    return@fold acc // If dot is negative, don't change the running sum (return acc to the fold lambda unchanged)
                else
                    normal.negate()
            }
            var adjVec = normal
            if (obj is NormTriangle) {
                adjVec = obj.planarNormal
                if (adjVec dot normal < 0)
                    adjVec.negate()
            }

            val shadowRay = Ray(point + 1e-10 * adjVec, lightVec)
            val intersections = findIntersections(shadowRay)
            when (light) {
                is PosLight -> {
                    if (intersections.any { it.value.distanceSquared(shadowRay.pos) <= lightDist })
                        return@fold acc // If point is in shadow, don't change the running sum
                }
                is DirLight -> {
                    if (intersections.isNotEmpty()) return@fold acc
                }
                else -> TODO(light.toString())
            }

            val lightRefl = lightVec - 2 * (lightVec dot normal) * normal
            lightRefl.negate()
            val viewVec = (camera.pos - point).apply { this.normalize() }
            val lightMod = when (light) {
                is PointLight, is DirLight -> 1.0
                is SpotLight -> {
                    val inner = cos(light.innerAngle)
                    val outer = cos(light.outerAngle)
                    val dot = -(lightVec dot light.dir)
                    when (dot) {
                        in inner..1.0 -> 1.0
                        in outer..inner -> (dot - outer) / inner
                        else -> 0.0
                    }
                }
                else -> TODO(light.toString())
            }
            val lighting = obj.specAt(point) * light.color * 1.0 / lightDist * (viewVec dot lightRefl).pow(obj.phongExpAt(point)) + obj.diffuseAt(point) * light.color * 1.0 / lightDist * (lightVec dot normal)
            return@fold acc + lighting * lightMod // Add lighting to the running sum
        } + ambient * obj.ambientAt(point) // Add ambient to the sum of lights
    }
}
