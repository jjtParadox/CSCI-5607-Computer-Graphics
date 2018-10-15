import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import java.util.*
import javax.vecmath.Point3d
import javax.vecmath.Tuple3d
import javax.vecmath.Vector3d
import kotlin.collections.LinkedHashMap
import kotlin.math.*
import kotlin.system.measureTimeMillis

fun createRaycast(w: Int, h: Int): Image {
    val camera = Camera(Point3d(0.0, 0.0, 0.0), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), 0.2, w.toDouble(), h.toDouble())
//    val sphere = Sphere(Point3d(90.0, 0.0, 0.0), 20.0, RayColor(200.0, 100.0, 100.0))
//    val smallSphere = Sphere(Point3d(77.0, 30.0, -7.0), 5.0, RayColor(100.0, 100.0, 200.0), diffuse = 0.5, spec = 1.0)
    val sphere = Sphere(Point3d(90.0, 0.0, 0.0), 20.0, Material(RayColor(200.0, 100.0, 100.0) / 255.0, RayColor(255.0, 2550.0, 255.0) / 255.0, RayColor(255.0, 255.0, 255.0) / 255.0*3.0, 30.0, RayColor(0.0, 0.0, 0.0) / 255.0, 0.0))
    val scene = Scene(camera, listOf(sphere), RayColor(40.0, 40.0, 40.0) / 255.0, Point3d(90.0, 300000.0, 0.0), RayColor(255.0, 255.0, 255.0) / 255.0 * 0.15)
    return createRaycast(scene)
}

fun createRaycast(scene: Scene): Image {
    val jitter = false
    val w = scene.camera.width.roundToInt()
    val h = scene.camera.height.roundToInt()
    val img = WritableImage(w, h)
    val px = img.pixelWriter

    val time = measureTimeMillis {
        for (x in 0 until w) {
            for (y in 0 until h) {
                val samples = LinkedList<RayColor>()
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
        val vector = (point - pos).apply { this.normalize() }
        return Ray(pos, vector)
    }
}

data class Ray(val pos: Point3d, val vec: Vector3d)

class RayColor : Tuple3d {
    constructor(r: Double, g: Double, b: Double) : super(r, g, b)
    constructor(t: Tuple3d) : super(t)

    operator fun plus(rayColor: RayColor) = RayColor(this).apply { this.add(rayColor) }
    operator fun minus(rayColor: RayColor) = RayColor(this).apply { this.sub(rayColor) }
    operator fun times(rayColor: RayColor) = RayColor(this).apply {
        this.x *= rayColor.x
        this.y *= rayColor.y
        this.z *= rayColor.z
    }
    operator fun times(d: Double) = RayColor(this).apply { this.scale(d) }
    operator fun div(d: Double) = RayColor(this).apply { this.scale(1.0/d) }
    fun lerp(rayColor: RayColor, alpha: Double) = RayColor(this).apply { this.interpolate(rayColor, alpha) }

//    fun toColor(): Color {
//        return Color(x.coerceIn(0.0, 255.0)/255.0,
//                y.coerceIn(0.0, 255.0)/255.0,
//                z.coerceIn(0.0, 255.0)/255.0,
//                1.0)
//    }
    fun toColor() = Color(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0), z.coerceIn(0.0, 1.0), 1.0)
}

class Material(var ambient: RayColor, var diffuse: RayColor, var specular: RayColor, var phongExp: Double, var trans: RayColor, var refraction: Double)

interface RayIntersector {
    fun intersect(ray: Ray): Point3d?

    fun colorAt(point: Point3d): RayColor
    fun diffuseAt(point: Point3d): RayColor
    fun specAt(point: Point3d): RayColor
    fun phongExpAt(point: Point3d): Double

    fun normalAt(point: Point3d): Vector3d
}

//data class Sphere(var pos: Point3d, var r: Double, var color: RayColor, var diffuse: Double = 1.0, var spec: Double = 3.0, var phong: Double = 30.0) : RayIntersector {
data class Sphere(var pos: Point3d, var r: Double, var material: Material) : RayIntersector {
    override fun intersect(ray: Ray): Point3d? {
        val a = ray.vec.lengthSquared()
        val b = 2.0*ray.vec dot (ray.pos - pos)
        val c = (ray.pos - pos).lengthSquared() - r*r

        val t1 = (-b + sqrt(b*b - 4*a*c))/(2*a)
        val t2 = (-b - sqrt(b*b - 4*a*c))/(2*a)
        if ((t1 < 0 || t1.isNaN()) && (t2 < 0 || t2.isNaN()))
            return null

        val p1 = ray.pos + t1 * ray.vec
        val p2 = ray.pos + t2 * ray.vec
        return if (p1.distanceSquared(ray.pos) < p2.distanceSquared(ray.pos)) p1 else p2
    }

    override fun colorAt(point: Point3d) = material.ambient//colorGradientAt(point)
    override fun diffuseAt(point: Point3d) = material.diffuse
    override fun specAt(point: Point3d) = material.specular
    override fun phongExpAt(point: Point3d) = material.phongExp

    override fun normalAt(point: Point3d) = (point - pos).apply { this.normalize() }

    private fun colorGradientAt(point: Point3d): RayColor {
        val vec = point - pos
        return material.ambient.lerp(RayColor(1.0, 1.0, 1.0) - material.ambient, (vec.z + r) / (2*r))
    }
}

class Scene(val camera: Camera, val objects: List<RayIntersector>, val background: RayColor = RayColor(0.0, 0.0, 0.0), val lightPos: Point3d?, val ambient: RayColor) {
    fun constructRay(i: Double, j: Double) = camera.constructRay(i, j)

    fun findIntersectionColor(ray: Ray): RayColor {
        val collisions = findIntersections(ray)
        val (obj, point) = collisions.minBy { it.value.distanceSquared(camera.pos) } ?: return background
        val color = obj.colorAt(point)
        return color * lightingAt(point, obj)
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
        if (lightPos == null) return ambient
        val lightVec = (lightPos - point).apply { this.normalize() }
        val normal = obj.normalAt(point)
        if (lightVec dot normal < 0) return ambient

        val shadowRay = Ray(point + 1e-10*normal, lightVec)
        if (findIntersections(shadowRay).isNotEmpty()) return ambient

        val lightRefl = lightVec - 2 * (lightVec dot normal) * normal
        lightRefl.negate()
        val viewVec = (camera.pos - point).apply { this.normalize() }
        val lighting = obj.specAt(point) * (viewVec dot lightRefl).pow(obj.phongExpAt(point)) + obj.diffuseAt(point) * (lightVec dot normal)
        return lighting + ambient
    }
}
