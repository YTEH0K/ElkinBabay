
package net.kaupenjoe.tutorialmod.client.renderer;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class RiftSdfMesher {

    private static final double GRID_STEP = 0.11D;
    private static final double SMOOTH_UNION = 0.10D;
    private static final long MAX_GRID_SAMPLES = 750_000L;

    private static final int[][] CORNERS = {
            {0, 0, 0},
            {1, 0, 0},
            {1, 1, 0},
            {0, 1, 0},
            {0, 0, 1},
            {1, 0, 1},
            {1, 1, 1},
            {0, 1, 1}
    };

    private static final int[][] TETRAHEDRA = {
            {0, 5, 1, 6},
            {0, 1, 2, 6},
            {0, 2, 3, 6},
            {0, 3, 7, 6},
            {0, 7, 4, 6},
            {0, 4, 5, 6}
    };

    private static final int[][] TETRA_EDGES = {
            {0, 1},
            {0, 2},
            {0, 3},
            {1, 2},
            {1, 3},
            {2, 3}
    };

    private RiftSdfMesher() {
    }

    record PathData(
            Vec3[] centers,
            float[] radii,
            Vec3[] normals,
            Vec3[] binormals,
            float twistOffset
    ) {
    }

    record Vertex(
            Vec3 position,
            Vec3 normal,
            float u,
            float v
    ) {
    }

    static final class Mesh {
        final List<Vertex> vertices;
        final float maxRadius;

        Mesh(List<Vertex> vertices, float maxRadius) {
            this.vertices = vertices;
            this.maxRadius = maxRadius;
        }

        boolean isEmpty() {
            return vertices.isEmpty();
        }

        static Mesh empty(float maxRadius) {
            return new Mesh(new ArrayList<>(), maxRadius);
        }
    }

    private record Segment(
            Vec3 start,
            Vec3 end,
            Vec3 normalStart,
            Vec3 normalEnd,
            Vec3 binormalStart,
            double radiusStart,
            double radiusEnd,
            double twistOffset,
            int index
    ) {
    }

    private record SurfacePoint(
            Vec3 position,
            Vec3 normal
    ) {
    }

    static Mesh build(List<PathData> paths) {
        if (paths.isEmpty()) {
            return Mesh.empty(0.0F);
        }

        List<Segment> segments = new ArrayList<>();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        double maxRadius = 0.0D;

        for (PathData path : paths) {
            int count = Math.min(
                    path.centers.length,
                    path.radii.length
            );

            for (int i = 0; i < count; i++) {
                Vec3 center = path.centers[i];

                minX = Math.min(minX, center.x);
                minY = Math.min(minY, center.y);
                minZ = Math.min(minZ, center.z);

                maxX = Math.max(maxX, center.x);
                maxY = Math.max(maxY, center.y);
                maxZ = Math.max(maxZ, center.z);

                maxRadius = Math.max(
                        maxRadius,
                        path.radii[i]
                );
            }

            for (int i = 0; i < count - 1; i++) {
                Vec3 start = path.centers[i];
                Vec3 end = path.centers[i + 1];

                if (start.distanceToSqr(end) < 0.00000001D) {
                    continue;
                }

                segments.add(new Segment(
                        start,
                        end,
                        path.normals[i],
                        path.normals[i + 1],
                        path.binormals[i],
                        path.radii[i],
                        path.radii[i + 1],
                        path.twistOffset,
                        i
                ));
            }
        }

        if (segments.isEmpty()) {
            return Mesh.empty((float) maxRadius);
        }

        // Учитываем небольшую неровность исходной поверхности
        // и добавляем запас для границ объёмной сетки.
        maxRadius *= 1.155D;

        double padding = maxRadius + GRID_STEP * 2.0D;

        minX -= padding;
        minY -= padding;
        minZ -= padding;

        maxX += padding;
        maxY += padding;
        maxZ += padding;

        int sizeX = Math.max(
                2,
                (int) Math.ceil((maxX - minX) / GRID_STEP)
        );

        int sizeY = Math.max(
                2,
                (int) Math.ceil((maxY - minY) / GRID_STEP)
        );

        int sizeZ = Math.max(
                2,
                (int) Math.ceil((maxZ - minZ) / GRID_STEP)
        );

        long sampleCount =
                (long) (sizeX + 1)
                        * (sizeY + 1)
                        * (sizeZ + 1);

        if (sampleCount > MAX_GRID_SAMPLES) {
            return Mesh.empty((float) maxRadius);
        }

        double stepX = (maxX - minX) / sizeX;
        double stepY = (maxY - minY) / sizeY;
        double stepZ = (maxZ - minZ) / sizeZ;

        int gridSizeY = sizeY + 1;
        int gridSizeZ = sizeZ + 1;

        int total = (int) sampleCount;

        float[] field = new float[total];

        // Этап 1. Вычисляем SDF для каждой точки объёмной сетки.
        for (int x = 0; x <= sizeX; x++) {
            for (int y = 0; y <= sizeY; y++) {
                for (int z = 0; z <= sizeZ; z++) {
                    int index = index(
                            x,
                            y,
                            z,
                            gridSizeY,
                            gridSizeZ
                    );

                    Vec3 point = new Vec3(
                            minX + x * stepX,
                            minY + y * stepY,
                            minZ + z * stepZ
                    );

                    field[index] = (float) distanceToUnion(
                            point,
                            segments
                    );
                }
            }
        }

        // Этап 2. Рассчитываем градиент поля.
        // Он будет использоваться как нормаль поверхности.
        float[] gradientX = new float[total];
        float[] gradientY = new float[total];
        float[] gradientZ = new float[total];

        for (int x = 0; x <= sizeX; x++) {
            int x0 = Math.max(0, x - 1);
            int x1 = Math.min(sizeX, x + 1);

            for (int y = 0; y <= sizeY; y++) {
                int y0 = Math.max(0, y - 1);
                int y1 = Math.min(sizeY, y + 1);

                for (int z = 0; z <= sizeZ; z++) {
                    int z0 = Math.max(0, z - 1);
                    int z1 = Math.min(sizeZ, z + 1);

                    int current = index(
                            x, y, z, gridSizeY, gridSizeZ
                    );

                    int left = index(
                            x0, y, z, gridSizeY, gridSizeZ
                    );

                    int right = index(
                            x1, y, z, gridSizeY, gridSizeZ
                    );

                    int down = index(
                            x, y0, z, gridSizeY, gridSizeZ
                    );

                    int up = index(
                            x, y1, z, gridSizeY, gridSizeZ
                    );

                    int back = index(
                            x, y, z0, gridSizeY, gridSizeZ
                    );

                    int front = index(
                            x, y, z1, gridSizeY, gridSizeZ
                    );

                    gradientX[current] =
                            (field[right] - field[left])
                                    / (float) ((x1 - x0) * stepX);

                    gradientY[current] =
                            (field[up] - field[down])
                                    / (float) ((y1 - y0) * stepY);

                    gradientZ[current] =
                            (field[front] - field[back])
                                    / (float) ((z1 - z0) * stepZ);
                }
            }
        }

        // Этап 3. Извлекаем единую поверхность.
        List<Vertex> vertices = new ArrayList<>();

        int[] cubeIndices = new int[8];
        int[] tetraIndices = new int[4];

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {

                    float cubeMin = Float.POSITIVE_INFINITY;
                    float cubeMax = Float.NEGATIVE_INFINITY;

                    for (int i = 0; i < 8; i++) {
                        int[] corner = CORNERS[i];

                        int id = index(
                                x + corner[0],
                                y + corner[1],
                                z + corner[2],
                                gridSizeY,
                                gridSizeZ
                        );

                        cubeIndices[i] = id;

                        cubeMin = Math.min(cubeMin, field[id]);
                        cubeMax = Math.max(cubeMax, field[id]);
                    }

                    // Весь куб снаружи или внутри поверхности.
                    if (cubeMin > 0.0F || cubeMax < 0.0F) {
                        continue;
                    }

                    for (int[] tetra : TETRAHEDRA) {
                        for (int i = 0; i < 4; i++) {
                            tetraIndices[i] = cubeIndices[tetra[i]];
                        }

                        polygonizeTetra(
                                tetraIndices,
                                field,
                                gradientX,
                                gradientY,
                                gradientZ,
                                gridSizeY,
                                gridSizeZ,
                                minX,
                                minY,
                                minZ,
                                stepX,
                                stepY,
                                stepZ,
                                minX,
                                minY,
                                rangeX,
                                rangeY,
                                vertices
                        );
                    }
                }
            }
        }

        return new Mesh(
                vertices,
                (float) maxRadius
        );
    }

    private static double distanceToUnion(
            Vec3 point,
            List<Segment> segments
    ) {
        double result = Double.POSITIVE_INFINITY;

        for (Segment segment : segments) {
            double distance = distanceToSegment(
                    point,
                    segment
            );

            if (Double.isInfinite(result)) {
                result = distance;
            } else {
                result = smoothMin(
                        result,
                        distance,
                        SMOOTH_UNION
                );
            }
        }

        return result;
    }

    private static double distanceToSegment(
            Vec3 point,
            Segment segment
    ) {
        Vec3 direction = segment.end.subtract(segment.start);

        double lengthSqr = direction.lengthSqr();

        double t = 0.0D;

        if (lengthSqr > 0.00000001D) {
            t = point.subtract(segment.start)
                    .dot(direction) / lengthSqr;

            t = Math.max(0.0D, Math.min(1.0D, t));
        }

        Vec3 closest = segment.start.add(
                direction.scale(t)
        );

        Vec3 radial = point.subtract(closest);

        double distance = radial.length();

        Vec3 tangent = direction.lengthSqr() > 0.00000001D
                ? direction.normalize()
                : new Vec3(0.0D, 1.0D, 0.0D);

        Vec3 frameNormal = segment.normalStart.lerp(
                segment.normalEnd,
                t
        );

        frameNormal = frameNormal.subtract(
                tangent.scale(frameNormal.dot(tangent))
        );

        if (frameNormal.lengthSqr() < 0.00000001D) {
            frameNormal = segment.normalStart;
        }

        frameNormal = frameNormal.normalize();

        Vec3 frameBinormal = tangent.cross(frameNormal).normalize();

        if (frameBinormal.lengthSqr() < 0.00000001D) {
            frameBinormal = segment.binormalStart;
        }

        double angle = Math.atan2(
                radial.dot(frameBinormal),
                radial.dot(frameNormal)
        );

        double ring = segment.index + t;

        // Сохраняем небольшую неровность исходной трубки.
        double irregularity =
                1.0D
                        + Math.sin(
                        (angle + ring * 0.08D
                                + segment.twistOffset) * 3.0D
                                + ring * 0.47D
                ) * 0.11D
                        + Math.cos(
                        (angle + ring * 0.08D
                                + segment.twistOffset) * 5.0D
                                - ring * 0.29D
                ) * 0.045D;

        double radius =
                segment.radiusStart
                        + (segment.radiusEnd - segment.radiusStart) * t;

        return distance - radius * irregularity;
    }

    private static double smoothMin(
            double a,
            double b,
            double k
    ) {
        double h = Math.max(
                k - Math.abs(a - b),
                0.0D
        ) / k;

        return Math.min(a, b) - h * h * k * 0.25D;
    }

    private static void polygonizeTetra(
            int[] ids,
            float[] field,
            float[] gradientX,
            float[] gradientY,
            float[] gradientZ,
            int gridSizeY,
            int gridSizeZ,
            double minX,
            double minY,
            double minZ,
            double stepX,
            double stepY,
            double stepZ,
            double uvMinX,
            double uvMinY,
            double uvRangeX,
            double uvRangeY,
            List<Vertex> output
    ) {
        SurfacePoint[] points = new SurfacePoint[4];
        int count = 0;

        for (int[] edge : TETRA_EDGES) {
            int idA = ids[edge[0]];
            int idB = ids[edge[1]];

            float valueA = field[idA];
            float valueB = field[idB];

            if ((valueA < 0.0F) == (valueB < 0.0F)) {
                continue;
            }

            double t = valueA / (double) (valueA - valueB);

            Vec3 positionA = gridPosition(
                    idA,
                    gridSizeY,
                    gridSizeZ,
                    minX,
                    minY,
                    minZ,
                    stepX,
                    stepY,
                    stepZ
            );

            Vec3 positionB = gridPosition(
                    idB,
                    gridSizeY,
                    gridSizeZ,
                    minX,
                    minY,
                    minZ,
                    stepX,
                    stepY,
                    stepZ
            );

            Vec3 position = positionA.lerp(positionB, t);

            Vec3 normal = new Vec3(
                    gradientX[idA]
                            + (gradientX[idB] - gradientX[idA]) * t,
                    gradientY[idA]
                            + (gradientY[idB] - gradientY[idA]) * t,
                    gradientZ[idA]
                            + (gradientZ[idB] - gradientZ[idA]) * t
            );

            if (normal.lengthSqr() > 0.00000001D) {
                normal = normal.normalize();
            }

            boolean duplicate = false;

            for (int i = 0; i < count; i++) {
                if (points[i].position.distanceToSqr(position)
                        < 0.000000000001D) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate && count < 4) {
                points[count++] = new SurfacePoint(
                        position,
                        normal
                );
            }
        }

        if (count < 3) {
            return;
        }

        Vec3 polygonNormal = Vec3.ZERO;

        for (int i = 0; i < count; i++) {
            polygonNormal = polygonNormal.add(points[i].normal);
        }

        if (polygonNormal.lengthSqr() < 0.00000001D) {
            polygonNormal = points[1].position
                    .subtract(points[0].position)
                    .cross(
                            points[2].position.subtract(
                                    points[0].position
                            )
                    );
        }

        if (polygonNormal.lengthSqr() < 0.00000001D) {
            return;
        }

        polygonNormal = polygonNormal.normalize();

        Vec3 reference = Math.abs(polygonNormal.y) < 0.85D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);

        Vec3 axisU = polygonNormal.cross(reference).normalize();
        Vec3 axisV = polygonNormal.cross(axisU).normalize();

        Vec3 center = Vec3.ZERO;

        for (int i = 0; i < count; i++) {
            center = center.add(points[i].position);
        }

        center = center.scale(1.0D / count);

        double[] angles = new double[4];

        for (int i = 0; i < count; i++) {
            Vec3 relative = points[i].position.subtract(center);

            angles[i] = Math.atan2(
                    relative.dot(axisV),
                    relative.dot(axisU)
            );
        }

        // Сортируем точки вокруг центра полигона.
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (angles[j] < angles[i]) {
                    double angle = angles[i];
                    angles[i] = angles[j];
                    angles[j] = angle;

                    SurfacePoint point = points[i];
                    points[i] = points[j];
                    points[j] = point;
                }
            }
        }

        addTriangle(
                points[0],
                points[1],
                points[2],
                uvMinX,
                uvMinY,
                uvRangeX,
                uvRangeY,
                output
        );

        if (count == 4) {
            addTriangle(
                    points[0],
                    points[2],
                    points[3],
                    uvMinX,
                    uvMinY,
                    uvRangeX,
                    uvRangeY,
                    output
            );
        }
    }

    private static void addTriangle(
            SurfacePoint pointA,
            SurfacePoint pointB,
            SurfacePoint pointC,
            double uvMinX,
            double uvMinY,
            double uvRangeX,
            double uvRangeY,
            List<Vertex> output
    ) {
        Vec3 faceNormal = pointB.position
                .subtract(pointA.position)
                .cross(
                        pointC.position.subtract(pointA.position)
                );

        if (faceNormal.lengthSqr() < 0.00000001D) {
            return;
        }

        Vec3 averageNormal = pointA.normal
                .add(pointB.normal)
                .add(pointC.normal);

        if (averageNormal.lengthSqr() > 0.00000001D
                && faceNormal.dot(averageNormal) < 0.0D) {
            SurfacePoint temp = pointB;
            pointB = pointC;
            pointC = temp;
        }

        output.add(toVertex(
                pointA,
                uvMinX,
                uvMinY,
                uvRangeX,
                uvRangeY
        ));

        output.add(toVertex(
                pointB,
                uvMinX,
                uvMinY,
                uvRangeX,
                uvRangeY
        ));

        output.add(toVertex(
                pointC,
                uvMinX,
                uvMinY,
                uvRangeX,
                uvRangeY
        ));
    }

    private static Vertex toVertex(
            SurfacePoint point,
            double minX,
            double minY,
            double rangeX,
            double rangeY
    ) {
        Vec3 normal = point.normal;

        if (normal.lengthSqr() < 0.00000001D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }

        float u = (float) ((point.position.x - minX) / rangeX);
        float v = (float) ((point.position.y - minY) / rangeY);

        return new Vertex(
                point.position,
                normal,
                u,
                v
        );
    }

    private static Vec3 gridPosition(
            int id,
            int gridSizeY,
            int gridSizeZ,
            double minX,
            double minY,
            double minZ,
            double stepX,
            double stepY,
            double stepZ
    ) {
        int x = id / (gridSizeY * gridSizeZ);
        int remainder = id % (gridSizeY * gridSizeZ);

        int y = remainder / gridSizeZ;
        int z = remainder % gridSizeZ;

        return new Vec3(
                minX + x * stepX,
                minY + y * stepY,
                minZ + z * stepZ
        );
    }

    private static int index(
            int x,
            int y,
            int z,
            int gridSizeY,
            int gridSizeZ
    ) {
        return (x * gridSizeY + y) * gridSizeZ + z;
    }
}
