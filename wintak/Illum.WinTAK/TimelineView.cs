using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Windows;
using System.Windows.Media;
using Illum.Core;

namespace Illum.WinTAK
{
    public sealed class TimelineView : FrameworkElement
    {
        public static readonly DependencyProperty ReportProperty = DependencyProperty.Register(nameof(Report), typeof(NightReport), typeof(TimelineView), new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty ZoneProperty = DependencyProperty.Register(nameof(Zone), typeof(DisplayZone), typeof(TimelineView), new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty SettingsProperty = DependencyProperty.Register(nameof(Settings), typeof(PlanSettings), typeof(TimelineView), new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));
        public NightReport Report { get => (NightReport)GetValue(ReportProperty); set => SetValue(ReportProperty, value); }
        public DisplayZone Zone { get => (DisplayZone)GetValue(ZoneProperty); set => SetValue(ZoneProperty, value); }
        public PlanSettings Settings { get => (PlanSettings)GetValue(SettingsProperty); set => SetValue(SettingsProperty,value); }
        private static Brush Brush(byte r,byte g,byte b)=>new SolidColorBrush(Color.FromRgb(r,g,b));
        protected override void OnRender(DrawingContext dc)
        {
            base.OnRender(dc); var n=Report; if(n?.sampleLux==null || ActualWidth<100 || ActualHeight<100) return;
            var settings=Settings??PlanSettings.defaults(); var zone=Zone??DisplayZone.ZULU;
            long t0=n.sunset==NightReport.NONE?n.spanStart:Math.Max(n.spanStart,n.sunset-3600000);
            long t1=n.sunrise==NightReport.NONE?n.spanEnd:Math.Min(n.spanEnd,n.sunrise+3600000);
            if(t1<=t0) { t0=n.spanStart; t1=n.spanEnd; }
            double width=ActualWidth,top=36,bottom=ActualHeight-36,moonTop=25,moonHeight=8,ratingHeight=8;
            Func<long,double> x=t=>(t-t0)/(double)(t1-t0)*width;
            Func<double,double> y=light=>top+(bottom-top)*(1-Math.Max(0,Math.Min(1,(Math.Log10(Math.Max(0.0001,light))+4)/4)));
            dc.DrawRectangle(Brush(14,19,25),null,new Rect(0,0,width,ActualHeight));
            dc.PushClip(new RectangleGeometry(new Rect(0,moonTop,width,bottom+ratingHeight-moonTop)));
            for(int i=0;i<n.sampleLux.Length;i++)
            {
                long ta=n.sampleTime(i),tb=ta+n.sampleStep; if(tb<t0 || ta>t1) continue;
                double xa=x(ta),xb=x(tb)+.5;
                double alt=n.sampleSunAlt[i];
                Brush sky=alt>=-.8333?Brush(79,107,133):alt>=-6?Brush(52,72,92):alt>=-12?Brush(36,50,65):alt>=-18?Brush(24,33,43):Brush(14,19,25);
                dc.DrawRectangle(sky,null,new Rect(xa,moonTop,xb-xa,bottom-moonTop));
                if(n.sampleMoonAlt[i]>0)
                {
                    bool visible=n.sampleMoonVisible==null || n.sampleMoonVisible[i];
                    dc.DrawRectangle(visible?Brush(255,245,157):Brush(109,106,76),null,new Rect(xa,moonTop,xb-xa,moonHeight));
                }
                bool good=n.sampleHll[i]==(settings.mission==PlanSettings.Mission.NVG);
                dc.DrawRectangle(good?Brush(46,125,50):Brush(198,40,40),null,new Rect(xa,bottom,xb-xa,ratingHeight));
            }
            var lux=new StreamGeometry(); var moon=new StreamGeometry(); bool started=false;
            using(var l=lux.Open()) using(var m=moon.Open())
                for(int i=0;i<n.sampleLux.Length;i++)
                {
                    long t=n.sampleTime(i); if(t<t0 || t>t1) continue;
                    double px=x(t),py=y(n.sampleLux[i]);
                    double my=top+(bottom-top)*(1-Math.Max(0,Math.Min(1,n.sampleMoonAlt[i]/90.0)));
                    if(!started) { l.BeginFigure(new Point(px,py),false,false); m.BeginFigure(new Point(px,my),false,false); started=true; }
                    else { l.LineTo(new Point(px,py),true,false); m.LineTo(new Point(px,my),true,false); }
                }
            dc.DrawGeometry(null,new Pen(Brushes.White,1.8),lux); dc.DrawGeometry(null,new Pen(Brushes.LightSkyBlue,1.2),moon);
            if(settings.standard.method==LightStandard.Method.LUX)
            {
                double ty=y(settings.standard.luxThreshold);
                dc.DrawLine(new Pen(Brushes.White,1) { DashStyle=DashStyles.Dash },new Point(0,ty),new Point(width,ty));
                Label(dc,Formats.lux(settings.standard.luxThreshold),width-4,ty-15,true);
            }
            if(n.hasWindow())
            {
                double a=x(n.windowStart),b=x(n.windowEnd);
                var dim=new SolidColorBrush(Color.FromArgb(125,0,0,0));
                if(a>0) dc.DrawRectangle(dim,null,new Rect(0,moonTop,Math.Min(a,width),bottom-moonTop));
                if(b<width) dc.DrawRectangle(dim,null,new Rect(Math.Max(0,b),moonTop,width-Math.Max(0,b),bottom-moonTop));
                foreach(double edge in new[] {a,b}) if(edge>=0 && edge<=width) dc.DrawLine(new Pen(Brushes.Cyan,1.5),new Point(edge,moonTop),new Point(edge,bottom+ratingHeight));
            }
            dc.Pop();
            // Two event-label lanes preserve nearby sunset/twilight/moonrise markers.
            var events=new List<Tuple<long,string>> { Tuple.Create(n.sunset,"SS"),Tuple.Create(n.eent,"EENT"),Tuple.Create(n.bmnt,"BMNT"),Tuple.Create(n.sunrise,"SR") };
            events.AddRange(n.moonEvents.Select(e=>Tuple.Create(e.time,e.rise?"MR":"MS")));
            double[] last={-100,-100};
            foreach(var e in events.Where(e=>e.Item1!=NightReport.NONE && e.Item1>=t0 && e.Item1<=t1).OrderBy(e=>e.Item1))
            {
                double px=x(e.Item1),labelX=Math.Max(3,Math.Min(width-34,px-14));
                int lane=labelX-last[0]>=40?0:1; if(labelX-last[lane]<40) continue;
                Label(dc,e.Item2,labelX,lane*11); last[lane]=labelX;
                dc.DrawLine(new Pen(Brush(193,207,219),1),new Point(px,22),new Point(px,moonTop+moonHeight));
            }
            long step=t1-t0>18*3600000L?4*3600000L:2*3600000L;
            long first=(long)Math.Ceiling(t0/(double)step)*step;
            for(long t=first;t<=t1;t+=step)
            {
                double px=x(t); dc.DrawLine(new Pen(Brush(193,207,219),1),new Point(px,bottom+ratingHeight),new Point(px,bottom+ratingHeight+3));
                Label(dc,Formats.time(t,zone),Math.Max(2,Math.Min(width-44,px-20)),bottom+ratingHeight+5);
            }
        }
        private void Label(DrawingContext dc,string text,double x,double y,bool alignRight=false)
        {
            var ft=new FormattedText(text,CultureInfo.InvariantCulture,FlowDirection.LeftToRight,new Typeface("Segoe UI"),10,
                new SolidColorBrush(Color.FromRgb(230,237,243)),VisualTreeHelper.GetDpi(this).PixelsPerDip);
            dc.DrawText(ft,new Point(alignRight?x-ft.Width:x,y));
        }
    }
}
