package com.aureliatransit.utilities.perf;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** javagl's rules as ATU's reader reproduces them; the exact match with javagl itself is checked by tools/objbench (parse). */
class FastObjParseTest {

	private static final String QUAD = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nvn 0 0 1\n";

	private static FastObjParse.Result parse(String obj, boolean split) {
		return FastObjParse.parse(obj.getBytes(StandardCharsets.UTF_8), split);
	}

	@Test
	void triangleKeepsCornerOrderAndData() {
		final FastObjParse.Mesh mesh = parse(QUAD + "usemtl a\nf 3/3/1 1/1/1 2/2/1\n", true).groups.get("default").get(0);
		assertEquals("a", mesh.material);
		assertEquals(3, mesh.vertexCount);
		// Vertices in order of first use: 3, 1, 2.
		assertArrayEquals(new float[]{1, 1, 0, 0, 0, 0, 1, 0, 0}, mesh.positions);
		assertArrayEquals(new float[]{1, 1, 0, 0, 1, 0}, mesh.uvs);
		assertArrayEquals(new int[]{0, 1, 2}, mesh.triangles);
		assertNotNull(mesh.normals);
	}

	@Test
	void polygonsBecomeFansAndShortFacesAreDropped() {
		final FastObjParse.Mesh mesh = parse(QUAD + "usemtl a\nf 1 2 3 4\nf 1 2\n", true).groups.get("default").get(0);
		assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3}, mesh.triangles);
		assertNull(mesh.uvs);
		assertNull(mesh.normals);
	}

	@Test
	void vertexWithSecondUvIsCopied() {
		final FastObjParse.Mesh mesh = parse(QUAD + "usemtl a\nf 1/1 2/2 3/3\nf 1/4 3/3 4/4\n", true).groups.get("default").get(0);
		// Vertex 1 with UV 4 becomes a fifth vertex at the end.
		assertEquals(5, mesh.vertexCount);
		assertArrayEquals(new int[]{0, 1, 2, 4, 2, 3}, mesh.triangles);
		assertEquals(0f, mesh.positions[12]);
		assertEquals(1f, mesh.uvs[9]);
	}

	@Test
	void groupsMaterialsAndFacesWithoutMaterial() {
		final FastObjParse.Result result = parse(QUAD + "f 1 2 3\ng body\nusemtl paint\nf 1 2 3\nusemtl glass\nf 2 3 4\ng doors body\nf 1 3 4\n", true);
		assertEquals(List.of("default", "body", "doors"), List.copyOf(result.groups.keySet()));
		// "default" only had a face before any usemtl: the group is there, without meshes.
		assertTrue(result.groups.get("default").isEmpty());
		final List<FastObjParse.Mesh> body = result.groups.get("body");
		assertEquals(2, body.size());
		assertEquals("paint", body.get(0).material);
		assertEquals(1, body.get(0).triangleCount());
		assertEquals(2, body.get(1).triangleCount());
		assertEquals(1, result.groups.get("doors").get(0).triangleCount());
	}

	@Test
	void firstOfGroupOrObjectWins() {
		assertEquals(List.of("a", "c"), List.copyOf(parse(QUAD + "usemtl m\ng a\nf 1 2 3\no b\nf 1 2 3\ng c\nf 1 2 3\n", true).groups.keySet()));
		assertEquals(List.of("a", "c"), List.copyOf(parse(QUAD + "usemtl m\no a\nf 1 2 3\ng b\nf 1 2 3\no c\nf 1 2 3\n", true).groups.keySet()));
	}

	@Test
	void relativeAndForwardIndices() {
		final FastObjParse.Mesh relative = parse(QUAD + "usemtl a\nf -1 -2 -3\n", true).groups.get("default").get(0);
		assertArrayEquals(new float[]{0, 1, 0, 1, 1, 0, 1, 0, 0}, relative.positions);
		assertNotNull(parse("usemtl a\nf 1 2 3\nv 0 0 0\nv 1 0 0\nv 0 1 0\n", true));
	}

	@Test
	void notSplitGivesOneEntry() {
		final FastObjParse.Result result = parse(QUAD + "usemtl a\ng x\nf 1 2 3\ng y\nf 2 3 4\n", false);
		assertEquals(List.of(""), List.copyOf(result.groups.keySet()));
		assertEquals(2, result.groups.get("").get(0).triangleCount());
	}

	@Test
	void lastMtllibCounts() {
		assertEquals(List.of("b.mtl"), parse("mtllib a.mtl\nmtllib  b.mtl \n", true).mtlFileNames);
	}

	@Test
	void numbersMatchParseFloat() {
		final String[] numbers = {"0.1", "-0", "1e-3", "+1.5", ".5", "5.", "123456.789", "0.30000000000000004", "16777217", "-12.3456789"};
		final StringBuilder obj = new StringBuilder();
		for (final String number : numbers) {
			obj.append("v ").append(number).append(" 0 0\n");
		}
		obj.append("usemtl a\n");
		for (int i = 1; i <= numbers.length; i++) {
			obj.append("f ").append(i).append(' ').append(i).append(' ').append(i).append('\n');
		}
		final FastObjParse.Mesh mesh = parse(obj.toString(), true).groups.get("default").get(0);
		for (int i = 0; i < numbers.length; i++) {
			assertEquals(Float.floatToRawIntBits(Float.parseFloat(numbers[i])), Float.floatToRawIntBits(mesh.positions[3 * i]), numbers[i]);
		}
	}

	@Test
	void unusualFilesAreLeftToJavagl() {
		assertNull(parse(QUAD + "usemtl a\nf 1 2 \\\n 3\n", true), "line continuation");
		assertNull(parse(QUAD + "usemtl a\nf 1\t2 3\n", true), "tab");
		assertNull(parse(QUAD + "usemtl a\nf 1/1 2 3/3\n", true), "mixed corner forms");
		assertNull(parse(QUAD + "usemtl a\nf 0 1 2\n", true), "index 0");
		assertNull(parse(QUAD + "usemtl a\nf 1 2 9\n", true), "index out of range");
		assertNull(parse("v 0 0 x\nusemtl a\n", true), "bad number");
		assertNull(parse("v 0 0\nusemtl a\n", true), "two coordinates");
		assertNull(parse(QUAD + "usemtl a\nf 1/ 2/ 3/\n", true), "trailing slash");
	}
}
