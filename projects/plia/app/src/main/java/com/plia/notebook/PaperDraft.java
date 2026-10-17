package com.plia.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** One immutable local input snapshot. Creating it does not submit a request. */
public final class PaperDraft {
    public final String kind, fingerprint;
    public final int selectedStrokeCount;
    public final PaperAnchor anchor;
    private final byte[] png;

    private PaperDraft(String kind, String fingerprint, int count, byte[] png, PaperAnchor anchor) {
        this.kind = kind;
        this.fingerprint = fingerprint;
        selectedStrokeCount = count;
        this.png = png.clone();
        this.anchor = anchor;
    }

    public byte[] pngCopy() { return png.clone(); }

    /** Input must contain only completed strokes. This method runs off the UI thread. */
    public static PaperDraft create(List<InkView.Stroke> frozen, int recentStart, String context) throws IOException {
        return create(frozen, recentStart, context, false);
    }

    /** The gesture is temporary UI input, never notebook ink or part of the uploaded image. */
    public static PaperDraft createSelection(List<InkView.Stroke> ink, List<InkView.Stroke> gesture, String context) throws IOException {
        if (gesture == null || gesture.isEmpty()) return null;
        ArrayList<float[]> edges=new ArrayList<>();
        for(InkView.Stroke stroke:gesture) {
            float[] xy=new float[stroke.points.size()*2];int index=0;
            for(InkView.Point point:stroke.points){xy[index++]=point.x;xy[index++]=point.y;}
            edges.add(xy);
        }
        SelectionOutline outline=null;
        for(int count=1;count<=4 && count<=edges.size();count++) {
            outline=SelectionOutline.from(edges.subList(edges.size()-count,edges.size()));
            if(outline!=null) break;
        }
        if(outline==null) return null;
        // Render the original stroke order, including erasers, then clip only the selected pixels.
        // Old invisible strokes and partially crossed ink cannot invalidate an otherwise closed loop.
        Bitmap full=InkView.bitmap(ink,true),image=null;
        try {
            int x=(int)Math.floor(outline.left),y=(int)Math.floor(outline.top);
            int width=Math.max(1,(int)Math.ceil(outline.right)-x),height=Math.max(1,(int)Math.ceil(outline.bottom)-y);
            image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);
            float[] points=outline.points();Path boundary=new Path();boundary.moveTo(points[0]-x,points[1]-y);
            for(int i=2;i<points.length;i+=2) boundary.lineTo(points[i]-x,points[i+1]-y);
            boundary.close();canvas.clipPath(boundary);canvas.drawBitmap(full,-x,-y,null);
            MessageDigest digest;
            try {digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
            if(!digestVisibleInk(image,digest)) return null;
            digest.update((context==null?"":context).getBytes(StandardCharsets.UTF_8));
            StringBuilder fingerprint=new StringBuilder();for(byte value:digest.digest()) fingerprint.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(!image.compress(Bitmap.CompressFormat.PNG,100,bytes) || bytes.size()>3*1024*1024) throw new IOException("Could not capture selection.");
            int count=0;for(InkView.Stroke stroke:ink) if(!stroke.erase) count++;
            return new PaperDraft(PaperSession.FOCUS,fingerprint.toString(),count,bytes.toByteArray(),
                    new PaperAnchor(outline.left,outline.top,outline.right,outline.bottom));
        } finally {if(image!=null) image.recycle();full.recycle();}
    }

    /** Newly drawn pen boundaries stay in the notebook, but only their enclosed ink is uploaded.
     * Loading an existing page never calls this path. There is no whole-page fallback. */
    public static PaperDraft createRecentBox(List<InkView.Stroke> ink, int recentStart, String context) throws IOException {
        for (int count = 1; count <= 4 && ink.size() - count >= recentStart; count++) {
            int boundary = ink.size() - count;
            List<InkView.Stroke> gesture=ink.subList(boundary,ink.size());
            // Ordinary writing still requires a recognized box/circle; Select allows freehand loops.
            ArrayList<BoxGesture.Trace> shape=new ArrayList<>();
            float l=1000,t=1600,r=0,b=0;
            for(InkView.Stroke stroke:gesture) for(InkView.Point p:stroke.points){l=Math.min(l,p.x);t=Math.min(t,p.y);r=Math.max(r,p.x);b=Math.max(b,p.y);}
            shape.add(new BoxGesture.Trace(new float[]{(l+r)/2,(t+b)/2,.5f},false));
            for(InkView.Stroke stroke:gesture){float[] p=new float[stroke.points.size()*3];int i=0;for(InkView.Point point:stroke.points){p[i++]=point.x;p[i++]=point.y;p[i++]=point.pressure;}shape.add(new BoxGesture.Trace(p,stroke.erase));}
            if(BoxGesture.detect(shape,1)==null) continue;
            PaperDraft draft = createSelection(ink.subList(0, boundary), gesture, context);
            if (draft != null) return draft;
        }
        return null;
    }

    private static PaperDraft create(List<InkView.Stroke> frozen, int recentStart, String context, boolean selectionOnly) throws IOException {
        ArrayList<BoxGesture.Trace> traces = new ArrayList<>();
        boolean hasInk = false, hasEraser = false, hasWhiteInk = false;
        for (InkView.Stroke stroke : frozen) {
            float[] points = new float[stroke.points.size() * 3];
            int p = 0;
            for (InkView.Point point : stroke.points) {
                points[p++] = point.x; points[p++] = point.y; points[p++] = point.pressure;
            }
            traces.add(new BoxGesture.Trace(points, stroke.erase));
            hasInk |= !stroke.erase;
            hasEraser |= stroke.erase;
            hasWhiteInk |= !stroke.erase && stroke.color == Color.WHITE;
        }
        if (!hasInk) return null;
        BoxGesture.Selection focus = BoxGesture.detect(traces, recentStart);
        if (selectionOnly && focus == null) return null;
        if (selectionOnly) for (int index : focus.selectedIndices) if (index >= recentStart) return null;
        int[] indices;
        if (focus == null) {
            indices = new int[frozen.size()];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
        } else indices = focus.selectedIndices.clone();

        ArrayList<InkView.Stroke> selected = new ArrayList<>();
        float left = InkView.WIDTH, top = InkView.HEIGHT, right = 0, bottom = 0;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        digest.update(BoxGesture.fingerprint(traces, indices).getBytes(StandardCharsets.UTF_8));
        for (int index : indices) {
            InkView.Stroke stroke = frozen.get(index);
            selected.add(stroke);
            int width = Float.floatToIntBits(stroke.width);
            digest.update(new byte[] {(byte)(width >>> 24), (byte)(width >>> 16), (byte)(width >>> 8), (byte)width});
            int color = stroke.color;
            digest.update(new byte[] {(byte)(color >>> 24), (byte)(color >>> 16), (byte)(color >>> 8), (byte)color});
            for (InkView.Point point : stroke.points) {
                left = Math.min(left, point.x - stroke.width - 14);
                top = Math.min(top, point.y - stroke.width - 14);
                right = Math.max(right, point.x + stroke.width + 14);
                bottom = Math.max(bottom, point.y + stroke.width + 14);
            }
        }
        digest.update((context == null ? "" : context).getBytes(StandardCharsets.UTF_8));
        StringBuilder fingerprint = new StringBuilder();
        for (byte b : digest.digest()) fingerprint.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));

        Bitmap full = InkView.bitmap(selected, true), image = full;
        try {
            if (focus != null) {
                int x = Math.max(0, (int)Math.floor(left)), y = Math.max(0, (int)Math.floor(top));
                int r = Math.min(InkView.WIDTH, (int)Math.ceil(right)), b = Math.min(InkView.HEIGHT, (int)Math.ceil(bottom));
                image = Bitmap.createBitmap(full, x, y, Math.max(1, r - x), Math.max(1, b - y));
            }
            if ((hasEraser || hasWhiteInk) && !hasRenderedInk(image)) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, bytes)) throw new IOException("Could not capture handwriting.");
            if (bytes.size() > 3 * 1024 * 1024) throw new IOException("This handwriting snapshot is too large.");
            PaperAnchor anchor = focus == null ? null : new PaperAnchor(focus.left, focus.top, focus.right, focus.bottom);
            return new PaperDraft(focus == null ? PaperSession.IDLE : PaperSession.FOCUS, fingerprint.toString(), indices.length, bytes.toByteArray(), anchor);
        } finally {
            if (image != full) image.recycle();
            full.recycle();
        }
    }

    private static boolean hasRenderedInk(Bitmap image) {
        int[] row = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int pixel : row) if ((pixel & 0xffffff) != 0xffffff) return true;
        }
        return false;
    }

    /** Normalize white margins so reselecting the same visible ink cannot duplicate a request. */
    private static boolean digestVisibleInk(Bitmap image,MessageDigest digest) {
        int width=image.getWidth(),height=image.getHeight(),left=width,top=height,right=-1,bottom=-1;
        int[] row=new int[width];
        for(int y=0;y<height;y++){image.getPixels(row,0,width,0,y,width,1);for(int x=0;x<width;x++) if((row[x]&0xffffff)!=0xffffff){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=y;}}
        if(right<left) return false;
        digest.update(((right-left+1)+":"+(bottom-top+1)+":").getBytes(StandardCharsets.UTF_8));
        for(int y=top;y<=bottom;y++){image.getPixels(row,0,width,0,y,width,1);for(int x=left;x<=right;x++){int p=row[x];digest.update((byte)(p>>16));digest.update((byte)(p>>8));digest.update((byte)p);}}
        return true;
    }
}
