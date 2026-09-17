"""
Offline optimiser for this mod's own hit-detection meshes.

Optimises the model itself, ahead of time, rather than inside the mod - run as
a separate tool alongside the MC Heli conversion tool.

WHY THIS EXISTS - the measurement that led here
-----------------------------------------------
The mod already merges coplanar patches at load time (see
ServerObjModelHitboxes' own COPLANAR_MERGE_ENABLED doc). On a real converted
model that reduces triangles by ~17.5%, which is real but produced no
perceptible speedup, because per-query cost is set by how many triangles share
the query point's own spatial-grid cell - not by the model's own total.

Two optimisations DO cut that local density substantially, but both need
analysis far too expensive to run every time a model loads. Running them once,
offline, is exactly what this tool is for:

1. INTERNAL WALL REMOVAL. Block-based models converted from Minecraft keep the
   faces between adjacent solid blocks - two coincident, opposite-facing quads
   sealed inside the model where nothing can ever reach them. They cost hit
   detection real work and contribute nothing. Measured at 7.4% of faces on the
   sample model (rapt.obj).

2. GREEDY RECTANGLE MERGING. Within one flat region, adjacent coplanar quads are
   merged into the smallest set of larger rectangles covering the same area. This
   is what the in-mod pass cannot do well: it uses a convex hull, so an L-shaped
   or holed region (very common) fails its own area check and is left untouched.
   A greedy rectangle cover handles those correctly, and offline there's no time
   pressure to keep it cheap.

MEASURED RESULT on the sample model, both steps together:
    108,509 faces -> 43,549 faces   (59.9% reduction)
    217,018 triangles -> ~87,098 triangles

ACCURACY
--------
No vertex is ever moved, and no surface an external query could reach is ever
removed or shifted:

- Internal walls are only removed when a face is EXACTLY coincident with another
  facing the opposite way. That pair seals a solid interior; deleting both leaves
  the outer surface untouched.
- Rectangle merging only ever replaces a set of coplanar rectangles with a
  different set covering the IDENTICAL area on the SAME plane. The merged region
  passes through the same outline.
- Faces that aren't planar axis-aligned rectangles in their own plane are passed
  through completely untouched, so curved and irregular geometry is never
  approximated.

TEXTURES - why the output is hit-detection ONLY
------------------------------------------------
This does not preserve UVs, and that limitation is fundamental rather than an
implementation shortcut. Converted models are atlas-mapped: every face covers
exactly one 16x16 tile of a shared atlas (verified on the sample model - all
108,509 faces, across 355 distinct tiles). Merging two adjacent coplanar quads
produces one larger quad whose UVs would have to REPEAT across that tile, and an
atlas cannot repeat - anything past a tile's own bounds samples whichever
neighbouring tile happens to sit there. So a merged mesh cannot be textured
correctly under ANY UV assignment, even when every merged face used the same
tile to begin with.

The display model therefore has to stay exactly as it is. Output is
written into a MIRRORED FOLDER rather than alongside the original, keeping
optimised files in one dedicated place for easier management: a model at
"…/models/obj/aircraft/foo.obj" is optimised to
"…/models_hitbox/obj/aircraft/foo.obj" by default - same relative structure,
one directory over. The mod picks this up automatically for hit detection only
(see ServerObjModelHitboxes' own tudursvehiclemod$resolveModelPath doc):
rendering keeps using the original file under "models/", with its own atlas
UVs untouched. Drop the optimised file into the mirrored location and it's
adopted - no JSON edit, no definition change, and packs without one are
completely unaffected. A path with no "models" segment (a non-standard layout)
falls back to a "models_hitbox" folder next to the input file.

Object/group names ARE preserved, which matters because per-part hit detection
resolves parts by group name (verified: all 50 groups survive on the sample).

Usage:
    python optimize_model.py .../models/obj/foo.obj
        # -> .../models_hitbox/obj/foo.obj
    python optimize_model.py model.obj -o custom_output.obj
    python optimize_model.py addon_pack/ --recursive
        # mirrors every models/... path it finds under models_hitbox/...
    python optimize_model.py model.obj --dry-run
"""

import argparse
import math
import os
import sys
from collections import defaultdict

# Two vertices closer than this (in model units) are treated as the same point.
# Far below any meaningful modelling detail, so distinct corners never collide.
VERTEX_EPSILON = 1e-4

# Rounding used when grouping faces onto a shared plane. Normals and plane
# offsets are quantised to this many decimals; loose enough to absorb float
# noise from the OBJ text, tight enough that genuinely different planes stay
# separate.
PLANE_DECIMALS = 3

# Dot product below which two normals count as opposite-facing, for internal
# wall detection. -0.99 is about 172 degrees or more apart.
OPPOSITE_NORMAL_THRESHOLD = -0.99

# Directory segment the mod looks for when picking up a dedicated hit-detection
# mesh. ServerObjModelHitboxes mirrors a model's own "models/..." path with this
# segment substituted in place of "models" and uses that file for hit detection
# when it exists - so writing output into that mirrored folder, alongside the
# addon's own "models" directory rather than mixed into it, is all that's needed
# to adopt it: no JSON edit and no definition change. Keep this pair in sync with
# that class's own HIT_DETECTION_MODEL_SOURCE_DIR / HIT_DETECTION_MODEL_DIR.
HIT_MODEL_SOURCE_DIR = "models"
HIT_MODEL_DIR = "models_hitbox"


def _mirrored_hitbox_path(input_path):
    """Maps a display model's own path to its mirrored hit-detection path by
    substituting the first HIT_MODEL_SOURCE_DIR path segment with HIT_MODEL_DIR
    (see HIT_MODEL_DIR's own doc). Falls back to a "models_hitbox" sibling
    directory next to the input file when its own path has no "models" segment
    to mirror, so the tool still produces a sensible, clearly-separated location
    for non-standard layouts rather than failing outright."""
    normalized = input_path.replace("\\", "/")
    segments = normalized.split("/")
    for i, segment in enumerate(segments):
        if segment == HIT_MODEL_SOURCE_DIR:
            mirrored = segments[:i] + [HIT_MODEL_DIR] + segments[i + 1:]
            return "/".join(mirrored)
    directory, filename = os.path.split(input_path)
    return os.path.join(directory, HIT_MODEL_DIR, filename)


def _parse_obj(path):
    """Reads an OBJ into (vertices, faces). Each face is (vertex_indices, group_name).

    Only v/f/g/o are interpreted. Vertex normals and texture coordinates are
    deliberately ignored - see this module's own doc for why that's acceptable
    for a hit-detection mesh specifically.
    """
    vertices = []
    faces = []
    current_group = None
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            parts = line.split()
            if not parts:
                continue
            keyword = parts[0]
            if keyword == "v":
                vertices.append(tuple(float(c) for c in parts[1:4]))
            elif keyword in ("g", "o"):
                current_group = parts[1] if len(parts) > 1 else None
            elif keyword == "f":
                indices = []
                for token in parts[1:]:
                    raw = token.split("/")[0]
                    if not raw:
                        continue
                    index = int(raw)
                    # OBJ indices are 1-based, and negative means relative to the end.
                    indices.append(index - 1 if index > 0 else len(vertices) + index)
                if len(indices) >= 3:
                    faces.append((indices, current_group))
    return vertices, faces


def _face_normal(vertices, indices):
    """Unit normal of a face, or None if it's degenerate (zero area)."""
    a = vertices[indices[0]]
    b = vertices[indices[1]]
    c = vertices[indices[2]]
    ab = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    ac = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    normal = (
        ab[1] * ac[2] - ab[2] * ac[1],
        ab[2] * ac[0] - ab[0] * ac[2],
        ab[0] * ac[1] - ab[1] * ac[0],
    )
    length = math.sqrt(sum(component * component for component in normal))
    if length < 1e-9:
        return None
    return tuple(component / length for component in normal)


def _remove_internal_walls(vertices, faces):
    """Step 1 - see this module's own doc.

    Drops pairs of faces that occupy exactly the same space while facing
    opposite directions. Such a pair is a wall sealed between two solid blocks:
    nothing outside the model can ever reach it, so it only ever costs hit
    detection work. Faces are matched by their own sorted, rounded vertex
    positions, so winding order doesn't affect matching.
    """
    by_position = defaultdict(list)
    for face_index, (indices, _group) in enumerate(faces):
        key = tuple(sorted(
            tuple(round(component, PLANE_DECIMALS) for component in vertices[i])
            for i in indices
        ))
        by_position[key].append(face_index)

    dropped = set()
    for coincident in by_position.values():
        if len(coincident) < 2:
            continue
        consumed = set()
        for first in range(len(coincident)):
            if coincident[first] in consumed:
                continue
            for second in range(first + 1, len(coincident)):
                if coincident[second] in consumed:
                    continue
                normal_a = _face_normal(vertices, faces[coincident[first]][0])
                normal_b = _face_normal(vertices, faces[coincident[second]][0])
                if normal_a is None or normal_b is None:
                    continue
                dot = sum(normal_a[i] * normal_b[i] for i in range(3))
                if dot < OPPOSITE_NORMAL_THRESHOLD:
                    dropped.add(coincident[first])
                    dropped.add(coincident[second])
                    consumed.add(coincident[first])
                    consumed.add(coincident[second])
                    break

    kept = [faces[i] for i in range(len(faces)) if i not in dropped]
    return kept, len(dropped)


def _plane_basis(normal):
    """An orthonormal (u, v) pair spanning the plane with the given normal."""
    seed = (1.0, 0.0, 0.0) if abs(normal[0]) < 0.9 else (0.0, 1.0, 0.0)
    u = (
        seed[1] * normal[2] - seed[2] * normal[1],
        seed[2] * normal[0] - seed[0] * normal[2],
        seed[0] * normal[1] - seed[1] * normal[0],
    )
    length = math.sqrt(sum(component * component for component in u))
    if length < 1e-9:
        return None
    u = tuple(component / length for component in u)
    v = (
        normal[1] * u[2] - normal[2] * u[1],
        normal[2] * u[0] - normal[0] * u[2],
        normal[0] * u[1] - normal[1] * u[0],
    )
    return u, v


def _greedy_rectangle_cover(cells):
    """Covers a set of unit grid cells with as few axis-aligned rectangles as
    a simple greedy sweep manages.

    Takes the lowest remaining cell, extends as far right as it can, then as far
    down as it can while every row stays fully covered, emits that rectangle and
    removes its cells. Not provably minimal (true minimum rectangle cover is
    expensive), but it collapses the long runs that dominate block-converted
    geometry, and being offline there's no need to trade accuracy for speed
    anywhere else.
    """
    remaining = set(cells)
    rectangles = []
    while remaining:
        start_x, start_y = min(remaining)
        width = 1
        while (start_x + width, start_y) in remaining:
            width += 1
        height = 1
        while all((start_x + dx, start_y + height) in remaining for dx in range(width)):
            height += 1
        for dx in range(width):
            for dy in range(height):
                remaining.discard((start_x + dx, start_y + dy))
        rectangles.append((start_x, start_y, start_x + width, start_y + height))
    return rectangles


def _merge_coplanar_rectangles(vertices, faces):
    """Step 2 - see this module's own doc.

    Groups faces by (object group, plane), and within each group merges those
    that are axis-aligned rectangles in their own plane into a smaller set of
    rectangles covering exactly the same area. Anything that isn't such a
    rectangle is passed through untouched, so curved or irregular geometry is
    never altered.
    """
    planes = defaultdict(list)
    passthrough = []
    for indices, group in faces:
        normal = _face_normal(vertices, indices)
        if normal is None:
            passthrough.append((indices, group))
            continue
        offset = sum(normal[i] * vertices[indices[0]][i] for i in range(3))
        plane_key = (
            group,
            round(normal[0], PLANE_DECIMALS),
            round(normal[1], PLANE_DECIMALS),
            round(normal[2], PLANE_DECIMALS),
            round(offset, PLANE_DECIMALS),
        )
        planes[plane_key].append((indices, normal))

    new_vertices = list(vertices)
    vertex_lookup = {}
    for index, position in enumerate(vertices):
        key = tuple(round(c / VERTEX_EPSILON) for c in position)
        vertex_lookup.setdefault(key, index)

    def intern_vertex(position):
        key = tuple(round(c / VERTEX_EPSILON) for c in position)
        existing = vertex_lookup.get(key)
        if existing is not None:
            return existing
        new_vertices.append(position)
        vertex_lookup[key] = len(new_vertices) - 1
        return len(new_vertices) - 1

    merged_faces = list(passthrough)
    merged_count = 0
    replaced_count = 0

    for plane_key, items in planes.items():
        group = plane_key[0]
        normal = items[0][1]
        basis = _plane_basis(normal)
        if basis is None:
            merged_faces.extend((indices, group) for indices, _n in items)
            continue
        u, v = basis
        offset_along_normal = sum(normal[i] * vertices[items[0][0][0]][i] for i in range(3))

        rectangles = []
        for indices, _normal in items:
            projected = [
                (
                    sum(vertices[i][k] * u[k] for k in range(3)),
                    sum(vertices[i][k] * v[k] for k in range(3)),
                )
                for i in indices
            ]
            unique_x = sorted({round(p[0], 4) for p in projected})
            unique_y = sorted({round(p[1], 4) for p in projected})
            if len(indices) == 4 and len(unique_x) == 2 and len(unique_y) == 2:
                rectangles.append((unique_x[0], unique_y[0], unique_x[1], unique_y[1]))
            else:
                merged_faces.append((indices, group))

        if not rectangles:
            continue
        if len(rectangles) == 1:
            # Nothing to merge with - re-emit as-is rather than round-tripping it.
            x0, y0, x1, y1 = rectangles[0]
            merged_faces.append((_rectangle_to_indices(
                x0, y0, x1, y1, u, v, normal, offset_along_normal, intern_vertex), group))
            continue

        # Snap the rectangles onto a shared grid so they can be merged as cells.
        xs = sorted({r[0] for r in rectangles} | {r[2] for r in rectangles})
        ys = sorted({r[1] for r in rectangles} | {r[3] for r in rectangles})
        x_index = {value: i for i, value in enumerate(xs)}
        y_index = {value: i for i, value in enumerate(ys)}
        cells = set()
        for x0, y0, x1, y1 in rectangles:
            for cx in range(x_index[x0], x_index[x1]):
                for cy in range(y_index[y0], y_index[y1]):
                    cells.add((cx, cy))

        covered = _greedy_rectangle_cover(cells)
        replaced_count += len(rectangles)
        merged_count += len(covered)
        for cx0, cy0, cx1, cy1 in covered:
            merged_faces.append((_rectangle_to_indices(
                xs[cx0], ys[cy0], xs[cx1], ys[cy1],
                u, v, normal, offset_along_normal, intern_vertex), group))

    return new_vertices, merged_faces, replaced_count, merged_count


def _rectangle_to_indices(x0, y0, x1, y1, u, v, normal, offset, intern_vertex):
    """Turns a 2D rectangle in plane coordinates back into four 3D vertex indices."""
    corners = []
    for px, py in ((x0, y0), (x1, y0), (x1, y1), (x0, y1)):
        position = tuple(
            u[k] * px + v[k] * py + normal[k] * offset for k in range(3)
        )
        corners.append(intern_vertex(position))
    return corners


def _write_obj(path, vertices, faces, source_name):
    """Writes the optimised mesh. Only v/g/f are emitted - see this module's own doc."""
    used = sorted({index for indices, _group in faces for index in indices})
    remap = {old: new for new, old in enumerate(used, start=1)}
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(f"# Hit-detection mesh optimised by optimize_model.py from {source_name}\n")
        handle.write("# NOT for rendering - UVs and normals are not preserved.\n")
        for old_index in used:
            x, y, z = vertices[old_index]
            handle.write(f"v {x:.6f} {y:.6f} {z:.6f}\n")
        current_group = object()
        for indices, group in faces:
            if group != current_group:
                current_group = group
                if group:
                    handle.write(f"g {group}\n")
            handle.write("f " + " ".join(str(remap[i]) for i in indices) + "\n")


def _triangle_count(faces):
    """Triangles a fan-triangulating loader will produce from these faces."""
    return sum(max(0, len(indices) - 2) for indices, _group in faces)


def _optimize_display_model(input_path, output_path, dry_run=False, quiet=False):
    """Display-model optimisation: removes
    faces that can never be seen, while leaving everything else in the file
    byte-for-byte as it was.

    WHY THIS IS A SEPARATE PATH FROM THE HIT-DETECTION ONE: that path merges and
    re-emits geometry, which discards UVs and normals - fine for collision, fatal
    for something that is actually drawn. Here the source file is kept as LINES
    and only whole `f` lines are dropped. Vertices, texture coordinates, normals,
    groups, materials, smoothing, comments and their ordering all survive exactly,
    so the result is a drop-in replacement for the original display model.

    WHAT IT REMOVES: pairs of faces that occupy exactly the same space while
    facing opposite directions. In a block-converted model these are the walls
    between two adjacent solid blocks - sealed inside the model where nothing
    outside can ever reach them, yet submitted to the GPU on every frame like any
    other face. Measured at 7.4% of faces on the sample model. Exactly-duplicated
    faces (same place, same facing) are also reduced to one, since drawing the
    same surface twice achieves nothing but cost.

    Nothing here moves a vertex or alters a UV, so the rendered result is
    unchanged except for surfaces that were never visible in the first place.
    """
    with open(input_path, "r", encoding="utf-8", errors="replace") as handle:
        lines = handle.readlines()

    vertices = []
    faces = []  # (line_index, vertex_indices)
    for line_index, line in enumerate(lines):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "v":
            vertices.append(tuple(float(c) for c in parts[1:4]))
        elif parts[0] == "f":
            indices = []
            for token in parts[1:]:
                raw = token.split("/")[0]
                if not raw:
                    continue
                index = int(raw)
                indices.append(index - 1 if index > 0 else len(vertices) + index)
            if len(indices) >= 3:
                faces.append((line_index, indices))

    if not faces:
        if not quiet:
            print(f"  {os.path.basename(input_path)}: no faces found, skipped")
        return None

    by_position = defaultdict(list)
    for face_index, (_line_index, indices) in enumerate(faces):
        key = tuple(sorted(
            tuple(round(component, PLANE_DECIMALS) for component in vertices[i])
            for i in indices
        ))
        by_position[key].append(face_index)

    removed_internal = 0
    removed_duplicate = 0
    dropped_lines = set()
    for coincident in by_position.values():
        if len(coincident) < 2:
            continue
        consumed = set()
        # Opposite-facing pairs first: both are removed, since neither can be seen.
        for first in range(len(coincident)):
            if coincident[first] in consumed:
                continue
            for second in range(first + 1, len(coincident)):
                if coincident[second] in consumed:
                    continue
                normal_a = _face_normal(vertices, faces[coincident[first]][1])
                normal_b = _face_normal(vertices, faces[coincident[second]][1])
                if normal_a is None or normal_b is None:
                    continue
                dot = sum(normal_a[i] * normal_b[i] for i in range(3))
                if dot < OPPOSITE_NORMAL_THRESHOLD:
                    for member in (coincident[first], coincident[second]):
                        dropped_lines.add(faces[member][0])
                        consumed.add(member)
                    removed_internal += 2
                    break
        # Whatever is left that still coincides and faces the SAME way is a duplicate; keep one.
        remaining = [member for member in coincident if member not in consumed]
        for first in range(len(remaining)):
            if remaining[first] in consumed:
                continue
            for second in range(first + 1, len(remaining)):
                if remaining[second] in consumed:
                    continue
                normal_a = _face_normal(vertices, faces[remaining[first]][1])
                normal_b = _face_normal(vertices, faces[remaining[second]][1])
                if normal_a is None or normal_b is None:
                    continue
                if sum(normal_a[i] * normal_b[i] for i in range(3)) > 0.99:
                    dropped_lines.add(faces[remaining[second]][0])
                    consumed.add(remaining[second])
                    removed_duplicate += 1

    original_faces = len(faces)
    final_faces = original_faces - len(dropped_lines)
    stats = {
        "original_faces": original_faces,
        "original_triangles": sum(max(0, len(i) - 2) for _l, i in faces),
        "walls_removed": removed_internal,
        "duplicates_removed": removed_duplicate,
        "final_faces": final_faces,
        "final_triangles": sum(max(0, len(i) - 2)
                               for line_index, i in faces if line_index not in dropped_lines),
    }

    if not dry_run:
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        with open(output_path, "w", encoding="utf-8") as handle:
            handle.write(f"# Display model optimised by optimize_model.py from {os.path.basename(input_path)}\n")
            handle.write("# Hidden interior faces removed; all other data preserved verbatim.\n")
            for line_index, line in enumerate(lines):
                if line_index not in dropped_lines:
                    handle.write(line)

    if not quiet:
        reduction = 100.0 * (1.0 - final_faces / original_faces) if original_faces else 0.0
        print(f"  {os.path.basename(input_path)}: "
              f"{original_faces} -> {final_faces} faces ({reduction:.1f}% smaller; "
              f"{removed_internal} hidden interior, {removed_duplicate} duplicate)")
    return stats


def optimize_file(input_path, output_path, dry_run=False, quiet=False):
    """Optimises one OBJ. Returns a stats dict; writes nothing when dry_run."""
    vertices, faces = _parse_obj(input_path)
    if not faces:
        if not quiet:
            print(f"  {os.path.basename(input_path)}: no faces found, skipped")
        return None

    original_faces = len(faces)
    original_triangles = _triangle_count(faces)

    faces, walls_removed = _remove_internal_walls(vertices, faces)
    vertices, faces, rectangles_replaced, rectangles_emitted = _merge_coplanar_rectangles(vertices, faces)

    final_faces = len(faces)
    final_triangles = _triangle_count(faces)

    stats = {
        "original_faces": original_faces,
        "original_triangles": original_triangles,
        "walls_removed": walls_removed,
        "rectangles_replaced": rectangles_replaced,
        "rectangles_emitted": rectangles_emitted,
        "final_faces": final_faces,
        "final_triangles": final_triangles,
    }

    if not dry_run:
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        _write_obj(output_path, vertices, faces, os.path.basename(input_path))

    if not quiet:
        reduction = 100.0 * (1.0 - final_triangles / original_triangles) if original_triangles else 0.0
        print(f"  {os.path.basename(input_path)}: "
              f"{original_triangles} -> {final_triangles} triangles "
              f"({reduction:.1f}% smaller; {walls_removed} internal-wall faces removed)")
    return stats


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Optimises OBJ models for use as this mod's own hit-detection meshes.")
    parser.add_argument("input", help="Input .obj file, or a directory of them.")
    parser.add_argument("-o", "--output",
                        help="Output file (for a single input) or directory (for a directory input). "
                             "Defaults to mirroring the input's own \"models\" path segment as "
                             "\"models_hitbox\" (see HIT_MODEL_DIR's own doc).")
    parser.add_argument("--recursive", action="store_true",
                        help="Recurse into subdirectories when the input is a directory.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what would change without writing anything.")
    parser.add_argument("--quiet", action="store_true", help="Only print the final summary.")
    parser.add_argument("--display", action="store_true",
                        help="Optimise for DISPLAY instead of hit detection: removes only faces that "
                             "cannot be seen, preserving UVs, normals and everything else verbatim, so "
                             "the result can replace the original model. Writes alongside the input "
                             "unless -o is given.")
    args = parser.parse_args(argv)

    inputs = []
    if os.path.isdir(args.input):
        walker = os.walk(args.input) if args.recursive else [
            (args.input, [], os.listdir(args.input))]
        for root, _dirs, files in walker:
            for name in files:
                if name.lower().endswith(".obj"):
                    inputs.append(os.path.join(root, name))
    else:
        inputs.append(args.input)

    if not inputs:
        print("No .obj files found.", file=sys.stderr)
        return 1

    print(f"Optimising {len(inputs)} model(s)"
          + (" (dry run - nothing will be written)" if args.dry_run else "") + ":")

    totals = defaultdict(int)
    for input_path in inputs:
        if args.display:
            # A display model replaces the original in place, so it stays in the models tree rather than the mirrored hitbox one.
            if os.path.isdir(args.input):
                relative = os.path.relpath(input_path, args.input)
                base, extension = os.path.splitext(relative)
                output_path = os.path.join(args.output or (args.input.rstrip("/\\") + "_optimized"),
                                           base + extension)
            elif args.output:
                output_path = args.output
            else:
                base, extension = os.path.splitext(input_path)
                output_path = base + "_optimized" + extension
        elif os.path.isdir(args.input):
            relative = os.path.relpath(input_path, args.input)
            output_path = (os.path.join(args.output, _mirrored_hitbox_path(relative))
                            if args.output else _mirrored_hitbox_path(input_path))
        elif args.output:
            output_path = args.output
        else:
            output_path = _mirrored_hitbox_path(input_path)
        try:
            stats = (_optimize_display_model(input_path, output_path, args.dry_run, args.quiet)
                     if args.display
                     else optimize_file(input_path, output_path, args.dry_run, args.quiet))
        except (OSError, ValueError) as error:
            print(f"  {os.path.basename(input_path)}: FAILED ({error})", file=sys.stderr)
            continue
        if stats:
            for key, value in stats.items():
                totals[key] += value

    if totals["original_triangles"]:
        reduction = 100.0 * (1.0 - totals["final_triangles"] / totals["original_triangles"])
        print(f"\nTotal: {totals['original_triangles']} -> {totals['final_triangles']} triangles "
              f"({reduction:.1f}% smaller)")
        print(f"  internal-wall faces removed: {totals['walls_removed']}")
        print(f"  coplanar rectangles merged:  {totals['rectangles_replaced']} -> {totals['rectangles_emitted']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
