"""
Converts Metasequoia (.mqo) model files to Wavefront .obj, for use by
mcheli_convert.py when an MC Heli addon pack's model files are in .mqo
format instead of .obj (both show up in the wild for MC Heli addons,
since Metasequoia is the tool MC Heli's own author originally used).
See https://www.metaseq.net/jp/format.html for the full MQO format spec
this is based on.

Covers what MC Heli addon models actually need in practice: one or more
named "Object" chunks, each with either an ASCII "vertex" or a binary
"BVertex" sub-chunk (see _read_bvertex_chunk's own doc), plus a "face"
sub-chunk. Does NOT handle Blob (metaball geometry), mirror/lathe
(procedural geometry - apply/"freeze" these in Metasequoia first so they
become plain vertex+face data), or object-level scale/rotation/translation
transforms (MC Heli addon models are typically authored directly in world
space already, at identity transform, to be combined directly - this
converter does NOT apply any transform chunks it encounters).

Materials are read only far enough to be ignored on purpose - this
project's own ObjModelLoader/ObjModel deliberately don't read OBJ
materials either, assigning one texture per vehicle via its own JSON
definition instead (see ObjModel's own doc) - so there's nothing useful
to do with MQO's own Material chunk here.

UV convention: Metasequoia's own V axis increases downward (0 at the top
of the texture, same as how the image file's own rows are stored) - the
same convention this project's own ObjModel.resolveVertexdirectly assumes
for OBJ's "vt" (it flips v itself when reading, assuming standard OBJ's
bottom-up convention) - so this converter flips V here (v_obj = 1 - v_mqo)
to compensate, letting that same flip-back land correctly on Metasequoia's
own top-down UVs.

Winding consistency: this project's own Java OBJ loader (ObjModel.java)
fan-triangulates n-gons from their first vertex, which is only correct for
CONVEX n-gons (see that loader's own documented assumption) - so every face
here is pre-triangulated via ear clipping instead (handles concave/
non-planar polygons correctly). On top of that, faces authored with
inconsistent winding relative to their neighbors (a common real-world
occurrence - e.g. a mirrored/copied part whose normals weren't flipped
along with it) are detected and corrected: see
_make_windings_consistent's own doc.
"""
import math
import re
import struct
from pathlib import Path

# Metasequoia's own default unit scale runs about 100x larger than what this
# project's OBJ models expect - see this constant's own call site in
# convert_mqo_to_obj(), applied to every vertex coordinate on the way out.
MQO_TO_OBJ_SCALE = 0.01


def parse_face_line(line: str) -> dict:
    """One line from inside a "face N { ... }" chunk:
    "%d V(%d ...) M(%d) UV(%.5f %.5f ...) COL(...) CRS(...)" - only the
    vertex-count, V(), and UV() parts are actually used.

    Indices and UVs are reversed here (relative to their MQO-declared
    order) - MQO's own vertex-order convention consistently comes out as
    the OPPOSITE of what this project's OBJ models expect (confirmed
    against real MC Heli addon data: a correctly-converted reference file's
    actual face windings are the reverse of MQO's own raw V(...) order,
    for every face checked, not just some). A global "flip if signed volume
    is negative" heuristic was tried first but isn't reliable for thin/open
    parts (a rotor blade, a thin wheel) where signed volume isn't a
    meaningful outward/inward signal at all - reversing each face
    individually, unconditionally, at the source is simpler and correct
    regardless of the part's own overall shape."""
    line = line.strip()
    v_match = re.search(r'V\(([^)]*)\)', line)
    indices = [int(tok) for tok in v_match.group(1).split()] if v_match else []
    uv_match = re.search(r'UV\(([^)]*)\)', line)
    uvs = []
    if uv_match:
        values = [float(tok) for tok in uv_match.group(1).split()]
        for k in range(0, len(values) - 1, 2):
            uvs.append((values[k], values[k + 1]))
    indices.reverse()
    uvs.reverse()
    return {"indices": indices, "uv": uvs}


class _LineCursor:
    """Reads mqo_path as raw bytes and yields text lines on demand via
    next_line(), like a file object's own readline() - but crucially, also
    supports read_bytes(n) to consume a fixed number of RAW bytes directly
    from the current position, for BVertex's own embedded binary Vector
    chunk (see _read_bvertex_chunk's own doc for why this can't just be
    line-split text like everything else here: a naive whole-file line
    split would corrupt any embedded binary bytes that happen to look like
    a newline)."""

    def __init__(self, path: Path):
        raw = path.read_bytes()
        if raw.startswith(b"\xef\xbb\xbf"):
            raw = raw[3:]  # UTF-8 BOM
        self._raw = raw
        self._pos = 0
        self._len = len(raw)

    def at_end(self) -> bool:
        return self._pos >= self._len

    def next_line(self) -> str:
        newline_pos = self._raw.find(b"\n", self._pos)
        if newline_pos == -1:
            chunk = self._raw[self._pos:]
            self._pos = self._len
        else:
            chunk = self._raw[self._pos:newline_pos]
            self._pos = newline_pos + 1
        if chunk.endswith(b"\r"):
            chunk = chunk[:-1]
        # Same encoding fallback reasoning as elsewhere in this project
        # (e.g. the Java-side WeaponStatsLoader) - try UTF-8 first, falling
        # back to Shift-JIS for older/Japanese-authored files, rather than
        # aborting the whole conversion over a handful of bad bytes usually
        # in a comment/name field that doesn't affect geometry.
        try:
            return chunk.decode("utf-8").strip()
        except UnicodeDecodeError:
            return chunk.decode("cp932", errors="replace").strip()

    def read_bytes(self, count: int) -> bytes:
        result = self._raw[self._pos:self._pos + count]
        self._pos += count
        # A trailing newline conventionally follows the binary payload
        # before the next text line resumes - consume it if present so
        # next_line() doesn't yield a stray empty line.
        if self._raw[self._pos:self._pos + 2] == b"\r\n":
            self._pos += 2
        elif self._raw[self._pos:self._pos + 1] == b"\n":
            self._pos += 1
        return result


def _read_bvertex_chunk(cursor: _LineCursor) -> list:
    """BVertex %d { Vector %d [%d] <raw bytes> weit {...} color {...} } -
    the binary equivalent of the ASCII "vertex" chunk (see the format
    spec's own note that BVertex was the default before Ver2.2, when ASCII
    vertex became standard instead - still shows up in older/non-default-
    setting exports). Returns [(x,y,z), ...], reading the Vector sub-chunk's
    raw little-endian float triples directly and skipping any other
    sub-chunks (weit/color) it doesn't need. Already past BVertex's own
    opening line when called."""
    vertices = []
    depth = 1
    while not cursor.at_end() and depth > 0:
        line = cursor.next_line()
        if line == "}":
            depth -= 1
            continue
        vector_match = re.match(r"Vector\s+(\d+)\s*\[(\d+)\]\s*$", line)
        if vector_match:
            count = int(vector_match.group(1))
            byte_size = int(vector_match.group(2))
            raw_floats = cursor.read_bytes(byte_size)
            values = struct.unpack(f"<{count * 3}f", raw_floats[:count * 12])
            for k in range(count):
                vertices.append((values[k * 3], values[k * 3 + 1], values[k * 3 + 2]))
            continue
        if line.endswith("{"):
            # weit/color or anything else nested in here - not needed, skip
            # its own body entirely.
            nested_depth = 1
            while not cursor.at_end() and nested_depth > 0:
                nested_line = cursor.next_line()
                if nested_line.endswith("{"):
                    nested_depth += 1
                elif nested_line == "}":
                    nested_depth -= 1
            continue
    return vertices


def parse_mqo_objects(mqo_path: Path) -> list:
    """Returns [{"name": str, "depth": int, "vertices": [(x,y,z), ...], "faces": [{"indices": [...], "uv": [(u,v), ...]}, ...]}, ...],
    one entry per top-level "Object" chunk, in file order.

    depth is Metasequoia's own object-hierarchy nesting level (0 = a
    top-level/root object; N = a child of the nearest PRECEDING object
    with depth N-1) - needed because MC Heli itself treats a "child"
    object as simply part of its own parent's part, not a separate part
    in its own right - see convert_mqo_to_obj()'s own doc for exactly how
    this gets merged back together on the way out."""
    cursor = _LineCursor(mqo_path)
    objects = []
    while not cursor.at_end():
        line = cursor.next_line()
        obj_match = re.match(r'Object\s+"((?:[^"\\]|\\.)*)"\s*\{\s*$', line)
        if not obj_match:
            continue

        name = obj_match.group(1)
        depth = 0
        vertices = []
        faces = []
        depth_level = 1  # inside the Object{} chunk itself
        while not cursor.at_end() and depth_level > 0:
            sub_line = cursor.next_line()
            if sub_line == "}":
                depth_level -= 1
                continue

            depth_field_match = re.match(r'depth\s+(\d+)\s*$', sub_line)
            vertex_match = re.match(r'vertex\s+(\d+)\s*\{\s*$', sub_line)
            bvertex_match = re.match(r'BVertex\s+(\d+)\s*\{\s*$', sub_line)
            face_match = re.match(r'face\s+(\d+)\s*\{\s*$', sub_line)
            if depth_field_match:
                depth = int(depth_field_match.group(1))
                continue
            if vertex_match:
                count = int(vertex_match.group(1))
                for _ in range(count):
                    parts = cursor.next_line().split()
                    vertices.append((float(parts[0]), float(parts[1]), float(parts[2])))
                cursor.next_line()  # this sub-chunk's own closing "}"
                continue
            if bvertex_match:
                vertices.extend(_read_bvertex_chunk(cursor))
                continue
            if face_match:
                count = int(face_match.group(1))
                for _ in range(count):
                    faces.append(parse_face_line(cursor.next_line()))
                cursor.next_line()  # this sub-chunk's own closing "}"
                continue
            if sub_line.endswith("{"):
                # Some other nested chunk this converter doesn't need
                # (vertexattr, mirror-related sub-chunks, etc.) - skip past
                # its own contents entirely by tracking its own brace depth.
                nested_depth = 1
                while not cursor.at_end() and nested_depth > 0:
                    nested_line = cursor.next_line()
                    if nested_line.endswith("{"):
                        nested_depth += 1
                    elif nested_line == "}":
                        nested_depth -= 1
                continue
            # A plain single-line field (uid, visible, scale, etc.) - not
            # needed by this converter (see this module's own doc for why
            # transforms specifically aren't applied) - just skip it.
        objects.append({"name": name, "depth": depth, "vertices": vertices, "faces": faces})
    return objects


def _polygon_normal(vertices_3d: list) -> tuple:
    """Newell's method - works for non-planar/concave polygons too (unlike a
    simple 3-point cross product), giving a reasonable best-fit normal to
    project the polygon into 2D for ear-clipping."""
    nx = ny = nz = 0.0
    n = len(vertices_3d)
    for i in range(n):
        x1, y1, z1 = vertices_3d[i]
        x2, y2, z2 = vertices_3d[(i + 1) % n]
        nx += (y1 - y2) * (z1 + z2)
        ny += (z1 - z2) * (x1 + x2)
        nz += (x1 - x2) * (y1 + y2)
    return (nx, ny, nz)


def _build_planar_basis(normal: tuple):
    """Returns (u_axis, v_axis) - two vectors perpendicular to normal and to
    each other, forming a 2D coordinate system on the polygon's own best-fit
    plane. Returns None if normal is degenerate (all vertices coincident or
    collinear - not a real polygon)."""
    nx, ny, nz = normal
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    if length < 1.0E-9:
        return None
    nx, ny, nz = nx / length, ny / length, nz / length
    reference = (1.0, 0.0, 0.0) if abs(nx) < 0.9 else (0.0, 1.0, 0.0)
    ux, uy, uz = (
        reference[1] * nz - reference[2] * ny,
        reference[2] * nx - reference[0] * nz,
        reference[0] * ny - reference[1] * nx,
    )
    u_len = math.sqrt(ux * ux + uy * uy + uz * uz)
    ux, uy, uz = ux / u_len, uy / u_len, uz / u_len
    vx, vy, vz = ny * uz - nz * uy, nz * ux - nx * uz, nx * uy - ny * ux
    return (ux, uy, uz), (vx, vy, vz)


def _triangulate_face(vertices_3d: list) -> list:
    """Returns a list of (i, j, k) LOCAL index triples (into vertices_3d/this
    face's own vertex list, 0-based) triangulating the polygon via ear
    clipping - correctly handles concave polygons, unlike a naive fan from
    vertex 0 (which this project's own Java OBJ loader does, and which only
    produces correct results for convex n-gons - see that loader's own
    documented assumption). Falls back to a plain fan for the rare
    degenerate/non-simple-polygon case ear clipping can't resolve (e.g. a
    self-intersecting outline), rather than dropping the face entirely."""
    n = len(vertices_3d)
    if n == 3:
        return [(0, 1, 2)]

    normal = _polygon_normal(vertices_3d)
    basis = _build_planar_basis(normal)
    if basis is None:
        return [(0, i, i + 1) for i in range(1, n - 1)]
    u_axis, v_axis = basis
    points_2d = []
    for (x, y, z) in vertices_3d:
        points_2d.append((x * u_axis[0] + y * u_axis[1] + z * u_axis[2],
                           x * v_axis[0] + y * v_axis[1] + z * v_axis[2]))

    signed_area = 0.0
    for i in range(n):
        x1, y1 = points_2d[i]
        x2, y2 = points_2d[(i + 1) % n]
        signed_area += x1 * y2 - x2 * y1
    ccw = signed_area > 0

    def is_convex_corner(a, b, c):
        ax, ay = points_2d[a]
        bx, by = points_2d[b]
        cx, cy = points_2d[c]
        cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        return cross > 0 if ccw else cross < 0

    def point_in_triangle(p, a, b, c):
        px, py = points_2d[p]
        ax, ay = points_2d[a]
        bx, by = points_2d[b]
        cx, cy = points_2d[c]

        def sign(x1, y1, x2, y2, x3, y3):
            return (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)

        d1 = sign(px, py, ax, ay, bx, by)
        d2 = sign(px, py, bx, by, cx, cy)
        d3 = sign(px, py, cx, cy, ax, ay)
        has_neg = d1 < 0 or d2 < 0 or d3 < 0
        has_pos = d1 > 0 or d2 > 0 or d3 > 0
        return not (has_neg and has_pos)

    remaining = list(range(n))
    triangles = []
    guard = 0
    while len(remaining) > 3 and guard < 10000:
        guard += 1
        found_ear = False
        m = len(remaining)
        for i in range(m):
            a = remaining[(i - 1) % m]
            b = remaining[i]
            c = remaining[(i + 1) % m]
            if not is_convex_corner(a, b, c):
                continue
            if any(p not in (a, b, c) and point_in_triangle(p, a, b, c) for p in remaining):
                continue
            triangles.append((a, b, c))
            del remaining[i]
            found_ear = True
            break
        if not found_ear:
            break  # degenerate/self-intersecting outline - fall back below for whatever's left
    if len(remaining) == 3:
        triangles.append(tuple(remaining))
    elif len(remaining) > 3:
        for i in range(1, len(remaining) - 1):
            triangles.append((remaining[0], remaining[i], remaining[i + 1]))
    return triangles


def _triangle_area_3d(v_a: tuple, v_b: tuple, v_c: tuple) -> float:
    ax, ay, az = v_b[0] - v_a[0], v_b[1] - v_a[1], v_b[2] - v_a[2]
    bx, by, bz = v_c[0] - v_a[0], v_c[1] - v_a[1], v_c[2] - v_a[2]
    cx, cy, cz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
    return 0.5 * math.sqrt(cx * cx + cy * cy + cz * cz)


def _make_windings_consistent(triangles: list, vertex_positions: list) -> list:
    """triangles: a flat list of (v_a, v_b, v_c, uv_a, uv_b, uv_c) for one
    whole Object (crossing original face boundaries - two originally
    separate polygons sharing an edge can be wound inconsistently relative
    to EACH OTHER too, not just within a single n-gon), where v_x are LOCAL
    vertex indices into vertex_positions and uv_x are (u, v) tuples already
    resolved per-corner. Returns a new list of the same shape, with some
    entries' vertex order (and matching uv order) reversed so that every
    pair of triangles sharing an edge is consistently wound (traverses that
    shared edge in opposite directions) - propagated per connected component
    via a flood fill from an arbitrary seed triangle in each.

    This corrects a common real-world authoring issue (e.g. a mirrored/
    copied part whose face normals weren't flipped back afterward) that
    otherwise shows up as some faces rendering "inside out" relative to
    their neighbors - exactly that happened ("some faces face
    left, some face right - they should all face the same way").

    Does NOT determine which overall direction is "outward" - only that
    neighbors agree with each other; a whole disconnected component could
    still end up uniformly inverted relative to the rest of the mesh. Since
    components only very rarely correspond to genuinely disconnected pieces
    of a single vehicle part in practice, this is a reasonable trade-off
    against the added complexity of also inferring outward-facing-ness
    (e.g. via signed volume), which isn't needed to fix the reported
    symptom.
    """
    n = len(triangles)
    if n == 0:
        return triangles

    # For each undirected edge (min_idx, max_idx), record which triangles
    # use it and in which direction (True = a->b as originally stored).
    edge_to_triangles = {}
    for tri_idx, (v_a, v_b, v_c, _, _, _) in enumerate(triangles):
        for (x, y) in ((v_a, v_b), (v_b, v_c), (v_c, v_a)):
            key = (x, y) if x < y else (y, x)
            forward = x < y
            edge_to_triangles.setdefault(key, []).append((tri_idx, forward))

    # Build an adjacency list: triangle -> [(neighbor, same_direction_bool)].
    # same_direction_bool True means the shared edge was stored in the SAME
    # direction by both triangles - i.e. INCONSISTENT winding (a proper
    # shared edge between two consistently-wound triangles is traversed in
    # OPPOSITE directions by each).
    adjacency = [[] for _ in range(n)]
    for key, users in edge_to_triangles.items():
        if len(users) != 2:
            continue  # a boundary edge (1 user) or a non-manifold edge (3+) - nothing to reconcile here
        (tri_a, dir_a), (tri_b, dir_b) = users
        same_direction = dir_a == dir_b
        adjacency[tri_a].append((tri_b, same_direction))
        adjacency[tri_b].append((tri_a, same_direction))

    visited = [False] * n
    flipped = [False] * n
    result = list(triangles)
    for start in range(n):
        if visited[start]:
            continue
        visited[start] = True
        stack = [start]
        while stack:
            current = stack.pop()
            for neighbor, same_direction in adjacency[current]:
                needs_flip = same_direction != flipped[current]
                if visited[neighbor]:
                    continue
                visited[neighbor] = True
                flipped[neighbor] = needs_flip
                stack.append(neighbor)

    for i in range(n):
        if flipped[i]:
            v_a, v_b, v_c, uv_a, uv_b, uv_c = result[i]
            result[i] = (v_a, v_c, v_b, uv_a, uv_c, uv_b)

    # Degenerate (near-zero-area) triangles can't meaningfully participate
    # in the adjacency check above (e.g. from a self-intersecting polygon's
    # ear-clipping fallback - see _triangulate_face's own doc) and are
    # filtered out entirely here instead, rather than risk them contributing
    # a "face constructed between vertices that shouldn't be connected" -
    # exactly that kind of artifact was observed.
    filtered = []
    for (v_a, v_b, v_c, uv_a, uv_b, uv_c) in result:
        area = _triangle_area_3d(vertex_positions[v_a], vertex_positions[v_b], vertex_positions[v_c])
        if area > 1.0E-8:
            filtered.append((v_a, v_b, v_c, uv_a, uv_b, uv_c))
    return filtered


def _compute_merge_targets(objects: list) -> list:
    """Returns a list (same length/order as objects) of merge-target
    INDICES - MC Heli itself treats an MQO
    "child" object as simply part of its own parent's part, not a
    separate part in its own right, so every object deeper than depth 1
    needs its own geometry folded into whichever ancestor sits at depth 0
    or 1 (the ACTUAL named parts, e.g. "$weapon0" - a depth-0 "root"
    wrapper object, typically named something generic like "ALL" and
    carrying no geometry of its own, is common MQO authoring practice and
    is deliberately left as ITS OWN separate group here rather than
    folding everything in the whole file down to one single group - only
    depth >= 2 actually merges upward)...

    EXCEPT: a genuinely-named part (this project's
    own "$..." naming convention - e.g. "$track_roller0") can itself be a
    DEEP child of another named part in MQO's own hierarchy (e.g. nested
    under "$crawler_track0") - purely an authoring/organizational choice
    in Metasequoia, not MC Heli's own intent that one becomes part of the
    other. Any object whose own name starts with "$" is therefore ALWAYS
    its own merge target regardless of depth, taking priority over the
    depth<=1 rule above - only a genuinely UNNAMED/generic child (a
    depth>=2 object that does NOT itself start with "$") ever actually
    folds into an ancestor.

    Standard "most recent object seen at each depth level" stack algorithm
    for resolving MQO's own flat, depth-tagged hierarchy: object i's own
    parent is the closest PRECEDING object whose own depth is exactly one
    less than object i's own depth.
    """
    merge_target = [0] * len(objects)
    ancestor_at_depth = {}  # depth level -> most recent object index seen at that depth
    for i, obj in enumerate(objects):
        depth = obj["depth"]
        ancestor_at_depth[depth] = i
        is_named_part = obj["name"].strip().startswith("$")
        if depth <= 1 or is_named_part:
            merge_target[i] = i
        else:
            parent = ancestor_at_depth.get(depth - 1)
            # A malformed/unexpected hierarchy (missing parent at the
            # expected depth) falls back to keeping this object as its
            # own separate group, rather than guessing at some other
            # ancestor and potentially merging unrelated geometry
            # together.
            merge_target[i] = merge_target[parent] if parent is not None else i
    return merge_target


def convert_mqo_to_obj(mqo_path: Path, obj_path: Path) -> int:
    """Writes obj_path from mqo_path's own Object chunks, one "g <name>"
    group per MQO object (matching this project's own ObjModel/
    ObjModelLoader convention of naming animated sub-parts via "o"/"g" -
    e.g. MC Heli's own "$rotor0"-style names carry straight through
    unchanged, since MQO object names end up as OBJ group names verbatim) -
    except for a "child" object (MQO's own depth-based hierarchy, depth
    >= 2 - see _compute_merge_targets()'s own doc), whose geometry is
    folded into its own nearest depth <= 1 ancestor's group instead of
    becoming its own separate one, since MC
    Heli itself treats these as simply part of their own parent's part.
    Uses "g" specifically rather than "o" - "g" seems
    to have better compatibility across other OBJ-consuming tools, and this
    project's own Java-side parser (ObjModel.java) already treats "o" and
    "g" completely identically, so there's no reason to prefer "o" here.
    Returns the number of objects converted (0 if the file had none, e.g.
    it wasn't actually a valid/supported MQO file at all).
    """
    objects = parse_mqo_objects(mqo_path)
    merge_target = _compute_merge_targets(objects)

    # Group object INDICES by their own merge target, preserving each
    # group's own first-appearance order (so the output file's own group
    # order still roughly matches the source file's own object order,
    # rather than e.g. alphabetizing by name).
    groups = {}
    group_order = []
    for i, target in enumerate(merge_target):
        if target not in groups:
            groups[target] = []
            group_order.append(target)
        groups[target].append(i)

    obj_path.parent.mkdir(parents=True, exist_ok=True)
    with obj_path.open("w", encoding="utf-8") as f:
        f.write(f"# Converted from {mqo_path.name} by mqo_to_obj.py\n")
        # OBJ's own "v"/"vt" numbering is GLOBAL across the WHOLE file, never
        # reset per group - multiple MQO Objects sharing one output file (the
        # common case for a vehicle's main model with named sub-parts, e.g.
        # "$rotor0") need their own face indices offset by how many
        # vertices/UVs every EARLIER object in this same file already wrote,
        # not just numbered from 1 each time - without this,
        # the following happens (parts not actually separating,
        # and faces connecting unrelated vertices from a different object
        # entirely).
        vertex_offset = 0
        vt_offset = 0
        for target in group_order:
            member_indices = groups[target]
            total_vertices = sum(len(objects[member]["vertices"]) for member in member_indices)
            if total_vertices == 0:
                continue  # a purely organizational wrapper object (e.g. a depth-0/1 "ALL"-style container with no geometry of its own) - nothing to actually write.
            # OBJ group names can't contain whitespace - MQO object names
            # occasionally do (e.g. a human-readable label rather than a
            # "$partname"-style machine name), so spaces are replaced with
            # underscores rather than silently truncating at the first one.
            # The GROUP's own name always comes from its own merge-target
            # object (the depth <= 1 ancestor) - never from a merged-in
            # child, even if that child happens to be listed here first
            # for some reason.
            safe_name = objects[target]["name"].strip().replace(" ", "_") or "unnamed"
            f.write(f"g {safe_name}\n")
            for member in member_indices:
                obj = objects[member]
                for x, y, z in obj["vertices"]:
                    # Metasequoia's own default unit scale runs about 100x
                    # larger than what this project's OBJ models expect -
                    # scaled down here so a converted .mqo model comes out
                    # the right size relative to everything else, per a
                    # direct request.
                    f.write(f"v {x * MQO_TO_OBJ_SCALE:.6f} {y * MQO_TO_OBJ_SCALE:.6f} {z * MQO_TO_OBJ_SCALE:.6f}\n")

                # Triangulate every face first (see _triangulate_face's own
                # doc), collecting the WHOLE object's triangles (with their
                # own per-corner UV already resolved) into one flat list
                # before writing anything out, so winding-consistency (see
                # _make_windings_consistent's own doc) can be enforced
                # across face boundaries too, not just within each original
                # polygon. Per-member (not per-group) - a merged-in child's
                # own faces never reference a DIFFERENT member's vertices,
                # so there's no need to widen this any further than each
                # member's own original faces.
                flat_triangles = []
                for face in obj["faces"]:
                    indices = face["indices"]
                    if len(indices) < 3:
                        continue  # a degenerate 1-2 vertex "face" - not a real polygon, skip
                    uvs = face["uv"]
                    face_vertices_3d = [obj["vertices"][idx] for idx in indices]
                    for (local_a, local_b, local_c) in _triangulate_face(face_vertices_3d):
                        v_a, v_b, v_c = indices[local_a], indices[local_b], indices[local_c]
                        uv_a = uvs[local_a] if local_a < len(uvs) else (0.0, 0.0)
                        uv_b = uvs[local_b] if local_b < len(uvs) else (0.0, 0.0)
                        uv_c = uvs[local_c] if local_c < len(uvs) else (0.0, 0.0)
                        flat_triangles.append((v_a, v_b, v_c, uv_a, uv_b, uv_c))

                flat_triangles = _make_windings_consistent(flat_triangles, obj["vertices"])

                vt_count = 0
                for (v_a, v_b, v_c, uv_a, uv_b, uv_c) in flat_triangles:
                    vt_indices = []
                    for (u, v) in (uv_a, uv_b, uv_c):
                        # Flip V - see this module's own top-level doc for why.
                        f.write(f"vt {u:.6f} {1.0 - v:.6f}\n")
                        vt_count += 1
                        vt_indices.append(vt_count + vt_offset)
                    tokens = [
                        f"{v_idx + 1 + vertex_offset}/{vt_idx}"
                        for v_idx, vt_idx in zip((v_a, v_b, v_c), vt_indices)
                    ]
                    f.write("f " + " ".join(tokens) + "\n")
                vertex_offset += len(obj["vertices"])
                vt_offset += vt_count
    return len(objects)
