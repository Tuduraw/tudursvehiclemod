"""Shared helpers for the sample addon pack generator.

Coordinate convention (matches the mod): +Z = forward, +X = left, +Y = up.
Every model is built from axis-aligned boxes. Each box belongs to a named
OBJ group ("o <name>"); animated parts use the mod's "$" prefix.

Texturing: one 128x128 atlas per vehicle, made of 16x16 colour swatches
(8x8 grid). Every face of a box maps to the inset centre of its swatch, so
each part renders as a flat colour without bleeding. A swatch may carry an
alpha < 255 (used for canopies / glass).
"""
import math, os, struct, subprocess
from PIL import Image, ImageDraw

SW = 16          # swatch size in px
GRID = 8         # swatches per row
TEX = SW * GRID  # 128


class ObjBuilder:
    def __init__(self, name):
        self.name = name
        self.v = []        # positions
        self.vt = []       # uvs
        self.vn = [(0,0,-1),(0,0,1),(-1,0,0),(1,0,0),(0,-1,0),(0,1,0)]
        self.faces = []    # (group, [(vi, ti, ni), ...])
        self.palette = []  # list of (name, (r,g,b,a))
        self.swatch_uv = {}  # palette index -> (vt base index)

    # ---- palette ----
    def color(self, name, rgba):
        for i,(n,c) in enumerate(self.palette):
            if n == name:
                return i
        self.palette.append((name, rgba))
        return len(self.palette) - 1

    def _uv_for(self, ci):
        if ci in self.swatch_uv:
            return self.swatch_uv[ci]
        col, row = ci % GRID, ci // GRID
        inset = 3.0
        u0 = (col*SW + inset) / TEX; u1 = ((col+1)*SW - inset) / TEX
        # OBJ v is bottom-up
        v1 = 1.0 - (row*SW + inset) / TEX; v0 = 1.0 - ((row+1)*SW - inset) / TEX
        base = len(self.vt)
        self.vt += [(u0,v0),(u1,v0),(u1,v1),(u0,v1)]
        self.swatch_uv[ci] = base
        return base

    # ---- geometry ----
    def box(self, group, cx, cy, cz, sx, sy, sz, ci, rot_y=0.0, rot_x=0.0, rot_z=0.0, pivot=None):
        """Axis-aligned box centred at (cx,cy,cz) with full sizes (sx,sy,sz),
        optionally rotated (degrees) about pivot (default: the box centre)."""
        hx, hy, hz = sx/2, sy/2, sz/2
        corners = [(-hx,-hy,-hz),( hx,-hy,-hz),( hx, hy,-hz),(-hx, hy,-hz),
                   (-hx,-hy, hz),( hx,-hy, hz),( hx, hy, hz),(-hx, hy, hz)]
        px,py,pz = pivot if pivot else (0,0,0)
        pts = []
        for x,y,z in corners:
            x,y,z = x+ (cx-px), y+(cy-py), z+(cz-pz)
            # rotate about X
            if rot_x:
                a=math.radians(rot_x); y,z = y*math.cos(a)-z*math.sin(a), y*math.sin(a)+z*math.cos(a)
            if rot_z:
                a=math.radians(rot_z); x,y = x*math.cos(a)-y*math.sin(a), x*math.sin(a)+y*math.cos(a)
            if rot_y:
                a=math.radians(rot_y); x,z = x*math.cos(a)+z*math.sin(a), -x*math.sin(a)+z*math.cos(a)
            pts.append((x+px, y+py, z+pz))
        base = len(self.v)
        self.v += pts
        t = self._uv_for(ci)
        # faces (quads), vertex order CCW seen from outside, normal index
        quads = [
            ([0,1,2,3], 0),  # -Z back
            ([5,4,7,6], 1),  # +Z front
            ([4,0,3,7], 2),  # -X
            ([1,5,6,2], 3),  # +X
            ([4,5,1,0], 4),  # -Y bottom
            ([3,2,6,7], 5),  # +Y top
        ]
        for idx, ni in quads:
            self.faces.append((group, [(base+idx[k]+1, t+k+1, ni+1) for k in range(4)]))

    def write(self, path, declare="g"):
        """declare: "o" (object) or "g" (group) - the OBJ keyword used to mark
        each named part. Both are accepted identically by this mod's own
        parser (ObjModel.java: `case "o", "g" -> ...`), but third-party
        software varies - some only reliably split sub-meshes on repeated
        "g" lines, and silently collapse repeated "o" lines into a single
        object instead. "g" is used for the actual sample pack for this
        reason; an "o"-based copy is also generated (see build_all.py) purely
        as a side-by-side reference, kept out of the loadable addon."""
        with open(path, "w", encoding="utf-8") as f:
            f.write(f"# {self.name} - generated low-poly sample model (Tudur's Vehicle Mod sample pack)\n")
            f.write(f"# +Z forward, +X left, +Y up. Units = blocks. Parts declared with \"{declare}\".\n")
            for x,y,z in self.v:
                f.write(f"v {x:.4f} {y:.4f} {z:.4f}\n")
            for u,v in self.vt:
                f.write(f"vt {u:.5f} {v:.5f}\n")
            for x,y,z in self.vn:
                f.write(f"vn {x} {y} {z}\n")
            cur = None
            for group, verts in self.faces:
                if group != cur:
                    f.write(f"{declare} {group}\n"); cur = group
                f.write("f " + " ".join(f"{a}/{b}/{c}" for a,b,c in verts) + "\n")

    def write_texture(self, path):
        img = Image.new("RGBA", (TEX, TEX), (0,0,0,0))
        d = ImageDraw.Draw(img)
        for i,(n,c) in enumerate(self.palette):
            col, row = i % GRID, i // GRID
            x0, y0 = col*SW, row*SW
            d.rectangle([x0, y0, x0+SW-1, y0+SW-1], fill=c)
            # subtle shading stripe for readability
            r,g,b,a = c
            dark = (max(r-25,0), max(g-25,0), max(b-25,0), a)
            d.rectangle([x0, y0+SW-3, x0+SW-1, y0+SW-1], fill=dark)
        img.save(path)


# ---------- dummy pilot skins ----------
def make_skin(path, skin=(224,188,150), hair=(60,40,30), shirt=(70,90,60), pants=(45,50,55),
              boots=(30,30,30), helmet=None, visor=None, sleeves=None, belt=None):
    """64x64 vanilla-layout skin with themed clothing.
    Base layer boxes (u,v,w,h,d): head(0,0,8,8,8) body(16,16,8,12,4) rarm(40,16,4,12,4) rleg(0,16,4,12,4)
    Overlay: hat(32,0) jacket(16,32) rsleeve(40,32) rpants(0,32) - this mod mirrors the right side for the left."""
    img = Image.new("RGBA", (64,64), (0,0,0,0)); d = ImageDraw.Draw(img)
    def boxuv(u,v,w,h,dep,top,bottom,side_front,side_back=None,left=None,right=None):
        side_back = side_back or side_front; left = left or side_front; right = right or side_front
        d.rectangle([u+dep, v, u+dep+w-1, v+dep-1], fill=top)           # top
        d.rectangle([u+dep+w, v, u+dep+2*w-1, v+dep-1], fill=bottom)   # bottom
        d.rectangle([u, v+dep, u+dep-1, v+dep+h-1], fill=right)         # right side
        d.rectangle([u+dep, v+dep, u+dep+w-1, v+dep+h-1], fill=side_front)   # front
        d.rectangle([u+dep+w, v+dep, u+dep+w+dep-1, v+dep+h-1], fill=left)  # left side
        d.rectangle([u+dep+w+dep, v+dep, u+2*dep+2*w-1, v+dep+h-1], fill=side_back)  # back
    # head: hair top/back, face front
    boxuv(0,0,8,8,8, top=hair, bottom=skin, side_front=skin, side_back=hair, left=skin, right=skin)
    # eyes + mouth
    d.point((10,12), fill=(255,255,255)); d.point((13,12), fill=(255,255,255))
    d.point((10,12), fill=(40,40,90)); d.point((13,12), fill=(40,40,90))
    d.line([(10,14),(13,14)], fill=(150,90,80))
    # hair fringe on forehead
    d.rectangle([8,8,15,9], fill=hair)
    # body
    boxuv(16,16,8,12,4, top=shirt, bottom=pants, side_front=shirt)
    if belt: d.rectangle([20,26,27,27], fill=belt)
    # right arm (mirrored for left by the mod)
    sl = sleeves or shirt
    boxuv(40,16,4,12,4, top=sl, bottom=skin, side_front=sl)
    d.rectangle([44,26,47,31], fill=skin)  # hand
    # right leg
    boxuv(0,16,4,12,4, top=pants, bottom=boots, side_front=pants)
    d.rectangle([4,29,7,31], fill=boots)
    # overlay: helmet
    if helmet:
        boxuv(32,0,8,8,8, top=helmet, bottom=(0,0,0,0), side_front=(0,0,0,0), side_back=helmet, left=helmet, right=helmet)
        d.rectangle([40,8,47,9], fill=helmet)  # brow band
        if visor:
            d.rectangle([40,10,47,12], fill=visor)
    img.save(path)


# ---------- sounds ----------
def make_sound(path, kind="engine", seconds=2.0, sr=22050):
    import numpy as np
    t = np.linspace(0, seconds, int(sr*seconds), endpoint=False)
    if kind == "engine":
        s = 0.35*np.sin(2*np.pi*110*t) + 0.2*np.sin(2*np.pi*220*t) + 0.15*np.random.uniform(-1,1,len(t))
        env = np.ones_like(t)
    elif kind == "rotor":
        s = 0.4*np.sign(np.sin(2*np.pi*18*t))*np.sin(2*np.pi*90*t) + 0.2*np.random.uniform(-1,1,len(t))
        env = np.ones_like(t)
    elif kind == "gun":
        s = np.random.uniform(-1,1,len(t)); env = np.exp(-t*25)
    elif kind == "launch":
        s = 0.5*np.sin(2*np.pi*(400-300*t/seconds)*t) + 0.4*np.random.uniform(-1,1,len(t)); env = np.exp(-t*3)
    elif kind == "boat":
        s = 0.3*np.sin(2*np.pi*60*t) + 0.3*np.random.uniform(-1,1,len(t)); env = np.ones_like(t)
    else:
        s = np.sin(2*np.pi*440*t); env = np.ones_like(t)
    s = s*env; s = s/np.max(np.abs(s)+1e-9)*0.8
    pcm = (s*32767).astype("<i2").tobytes()
    wav = path + ".wav"
    with open(wav, "wb") as f:
        f.write(b"RIFF"+struct.pack("<I",36+len(pcm))+b"WAVEfmt "+struct.pack("<IHHIIHH",16,1,1,sr,sr*2,2,16)+b"data"+struct.pack("<I",len(pcm))+pcm)
    subprocess.run(["ffmpeg","-y","-loglevel","error","-i",wav,"-c:a","libvorbis","-q:a","3",path], check=True)
    os.remove(wav)
