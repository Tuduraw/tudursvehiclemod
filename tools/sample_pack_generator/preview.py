import sys, math
from PIL import Image, ImageDraw

def load(obj_path, tex_path):
    tex = Image.open(tex_path).convert("RGBA")
    V=[];VT=[];faces=[]
    for line in open(obj_path):
        p=line.split()
        if not p: continue
        if p[0]=="v": V.append(tuple(map(float,p[1:4])))
        elif p[0]=="vt": VT.append(tuple(map(float,p[1:3])))
        elif p[0]=="f":
            vs=[]; c=None
            for tok in p[1:]:
                i=tok.split("/"); vs.append(V[int(i[0])-1])
                if c is None:
                    u,v=VT[int(i[1])-1]; c=tex.getpixel((int(u*tex.width)%tex.width, int((1-v)*tex.height)%tex.height))
            faces.append((vs,c))
    return faces

def render(faces, yaw=35, pitch=25, size=600):
    ya,pa=math.radians(yaw),math.radians(pitch)
    def proj(x,y,z):
        x,z = x*math.cos(ya)+z*math.sin(ya), -x*math.sin(ya)+z*math.cos(ya)
        y,z = y*math.cos(pa)-z*math.sin(pa), y*math.sin(pa)+z*math.cos(pa)
        return x,y,z
    tris=[]
    for vs,c in faces:
        pv=[proj(*v) for v in vs]
        n=[(a+b)/2 for a,b in zip(pv[0],pv[2])]
        # normal via cross product for shading
        ax,ay,az=[pv[1][i]-pv[0][i] for i in range(3)]; bx,by,bz=[pv[2][i]-pv[0][i] for i in range(3)]
        nx,ny,nz=ay*bz-az*by, az*bx-ax*bz, ax*by-ay*bx
        l=math.sqrt(nx*nx+ny*ny+nz*nz)+1e-9; shade=0.6+0.4*max(0,(ny*0.7+nz*0.7)/l)
        if nz<0: continue  # back-face
        tris.append((sum(p[2] for p in pv)/len(pv), pv, tuple(int(ch*shade) for ch in c[:3])+(c[3],)))
    tris.sort(key=lambda t:t[0])
    xs=[p[0] for _,pv,_ in tris for p in pv]; ys=[p[1] for _,pv,_ in tris for p in pv]
    if not xs: return Image.new("RGBA",(size,size),(200,200,200,255))
    s=(size*0.9)/max(max(xs)-min(xs),max(ys)-min(ys),1e-6); cx,cy=(max(xs)+min(xs))/2,(max(ys)+min(ys))/2
    img=Image.new("RGBA",(size,size),(200,205,210,255)); d=ImageDraw.Draw(img,"RGBA")
    for _,pv,c in tris:
        pts=[((p[0]-cx)*s+size/2, size/2-(p[1]-cy)*s) for p in pv]
        d.polygon(pts, fill=c, outline=(0,0,0,60))
    return img

if __name__=="__main__":
    base=sys.argv[1]; names=sys.argv[2:]
    imgs=[render(load(f"{base}/models/obj/{n}.obj", f"{base}/textures/vehicle/{n}.png"), size=360) for n in names]
    sheet=Image.new("RGBA",(360*len(imgs),360)); 
    for i,im in enumerate(imgs): sheet.paste(im,(360*i,0))
    sheet.save("/tmp/preview/models.png"); print("ok")
