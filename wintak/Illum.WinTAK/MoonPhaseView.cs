using System;
using System.Globalization;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Media;
using Illum.Core;

namespace Illum.WinTAK
{
    /// <summary>The same illuminated-fraction and hemisphere convention as ATAK MoonPhaseView.</summary>
    public sealed class MoonPhaseView : FrameworkElement
    {
        public static readonly DependencyProperty ReportProperty = DependencyProperty.Register(nameof(Report),typeof(NightReport),typeof(MoonPhaseView),
            new FrameworkPropertyMetadata(null,FrameworkPropertyMetadataOptions.AffectsRender,Changed));
        public NightReport Report { get=>(NightReport)GetValue(ReportProperty); set=>SetValue(ReportProperty,value); }
        private static void Changed(DependencyObject d,DependencyPropertyChangedEventArgs e)
        {
            var n=e.NewValue as NightReport;
            string label=n==null?"Moon phase":string.Format(CultureInfo.InvariantCulture,"Moon {0:0}% illuminated, {1}; {2} hemisphere",n.illumPercent,n.waxing?"waxing":"waning",n.latDeg<0?"southern":"northern");
            AutomationProperties.SetName(d,label); ((MoonPhaseView)d).ToolTip=label;
        }
        protected override void OnRender(DrawingContext dc)
        {
            base.OnRender(dc); var n=Report; if(n==null) return;
            double radius=Math.Min(ActualWidth,ActualHeight)/2-2;
            if(radius<=0) return;
            double cx=ActualWidth/2,cy=ActualHeight/2;
            double k=Math.Max(0,Math.Min(1,n.illumPercent/100));
            var dark=new SolidColorBrush(Color.FromRgb(28,37,47)); var lit=new SolidColorBrush(Color.FromRgb(242,238,219));
            dc.DrawEllipse(dark,null,new Point(cx,cy),radius,radius);
            if(k>=.995) dc.DrawEllipse(lit,null,new Point(cx,cy),radius,radius);
            else if(k>.005)
            {
                double mirror=n.waxing==(n.latDeg<0)?-1:1;
                var shape=new StreamGeometry();
                using(var path=shape.Open())
                {
                    path.BeginFigure(new Point(cx,cy-radius),true,true);
                    for(int i=1;i<=100;i++)
                    { double a=-Math.PI/2+Math.PI*i/100; path.LineTo(new Point(cx+mirror*radius*Math.Cos(a),cy+radius*Math.Sin(a)),true,false); }
                    // Terminator half-ellipse, bottom to top. Its sign changes at quarter phase.
                    for(int i=100;i>=0;i--)
                    { double a=-Math.PI/2+Math.PI*i/100; path.LineTo(new Point(cx+mirror*radius*(1-2*k)*Math.Cos(a),cy+radius*Math.Sin(a)),true,false); }
                }
                dc.DrawGeometry(lit,null,shape);
            }
            dc.DrawEllipse(null,new Pen(new SolidColorBrush(Color.FromRgb(170,187,202)),1),new Point(cx,cy),radius,radius);
        }
    }
}
