package com.plia.notebook;

import java.util.ArrayList;
import java.util.List;

/** A closed, hand-drawn selection. No assumption that its edges are axis-aligned. */
public final class SelectionOutline {
    public final float left, top, right, bottom;
    private final float[] xy;
    private SelectionOutline(float l, float t, float r, float b, float[] xy) {
        left=l; top=t; right=r; bottom=b; this.xy=xy;
    }
    public float[] points() { return xy.clone(); }

    public static SelectionOutline from(List<float[]> strokes) {
        if (strokes == null || strokes.isEmpty() || strokes.size()>4) return null;
        float l=Float.POSITIVE_INFINITY,t=l,r=-l,b=-l;
        for (float[] p:strokes) {
            if(p==null || p.length<4 || p.length%2!=0 || p.length>16000) return null;
            for(int i=0;i<p.length;i+=2) {
                if(!Float.isFinite(p[i]) || !Float.isFinite(p[i+1]) || p[i]<0 || p[i]>1000 || p[i+1]<0 || p[i+1]>1600) return null;
                l=Math.min(l,p[i]);t=Math.min(t,p[i+1]);r=Math.max(r,p[i]);b=Math.max(b,p[i+1]);
            }
        }
        float w=r-l,h=b-t;
        if(w<32 || h<26 || Math.max(w,h)/Math.min(w,h)>20) return null;
        float tolerance=Math.max(10,Math.min(30,Math.min(w,h)*.18f));
        ArrayList<Float> ordered=new ArrayList<>();
        boolean[] used=new boolean[strokes.size()];
        if(!join(strokes,used,ordered,tolerance)) return null;
        float[] points=new float[ordered.size()];
        for(int i=0;i<points.length;i++) points[i]=ordered.get(i);
        double twiceArea=0,length=0;
        for(int i=0;i<points.length;i+=2) {
            int next=(i+2)%points.length;
            twiceArea+=(double)points[i]*points[next+1]-(double)points[next]*points[i+1];
            length+=Math.hypot(points[i]-points[next],points[i+1]-points[next+1]);
        }
        double area=Math.abs(twiceArea)*.5;
        if(area<w*h*.30 || area>w*h*1.15 || length<(w+h)*1.3 || length>(w+h)*4.5) return null;
        return new SelectionOutline(l,t,r,b,points);
    }

    private static boolean join(List<float[]> strokes,boolean[] used,ArrayList<Float> out,float tolerance) {
        boolean all=true;for(boolean value:used) all&=value;
        if(all) return Math.hypot(out.get(0)-out.get(out.size()-2),out.get(1)-out.get(out.size()-1))<=tolerance;
        for(int index=0;index<strokes.size();index++) if(!used[index]) {
            float[] p=strokes.get(index);
            for(int direction=0;direction<2;direction++) {
                int start=direction==0?0:p.length-2;
                if(!out.isEmpty() && Math.hypot(out.get(out.size()-2)-p[start],out.get(out.size()-1)-p[start+1])>tolerance) continue;
                int saved=out.size();used[index]=true;
                for(int i=start;i>=0 && i<p.length;i+=direction==0?2:-2) {out.add(p[i]);out.add(p[i+1]);}
                if(join(strokes,used,out,tolerance)) return true;
                used[index]=false;while(out.size()>saved) out.remove(out.size()-1);
            }
            if(out.isEmpty()) break; // Any closed loop can start with the first stroke.
        }
        return false;
    }
}
