using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Runtime.Remoting.Messaging;
using System.Runtime.Remoting.Proxies;
using System.Text;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using System.Windows.Input;
using Illum.Core;
using Illum.WinTAK;
using WinTak.Common.Services;
using WinTak.Display;
using WinTak.Framework;
using WinTak.Mapping;
using Control = System.Windows.Controls.Control;

namespace Illum.SdkCheck
{
    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
            string sdk=args.Length>0?Path.GetFullPath(args[0]):Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"WinTAK");
            string artifacts=args.Length>1?Path.GetFullPath(args[1]):Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"artifacts");
            AppDomain.CurrentDomain.AssemblyResolve+=(s,e)=> { string path=Path.Combine(sdk,new AssemblyName(e.Name).Name+".dll"); return File.Exists(path)?Assembly.LoadFrom(path):null; };
            Environment.SetEnvironmentVariable("PATH",sdk+";"+Environment.GetEnvironmentVariable("PATH"));
            try { Run(artifacts); return 0; } catch(Exception e) { Console.Error.WriteLine(e); return 1; }
        }
        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void Run(string artifacts)
        {
            Directory.CreateDirectory(artifacts);
            var app=new Application();
            SynchronizationContext.SetSynchronizationContext(new DispatcherSynchronizationContext(Dispatcher.CurrentDispatcher));
            ValidateRegistrationNames();
            // Reproduce a host with inherited white text and pale native controls.
            app.Resources.Add(typeof(Button),new Style(typeof(Button)) { Setters={new Setter(Control.ForegroundProperty,Brushes.White),new Setter(Control.BackgroundProperty,Brushes.White)} });
            var errors=new StringWriter(); var listener=new TextWriterTraceListener(errors);
            PresentationTraceSources.DataBindingSource.Listeners.Add(listener);
            PresentationTraceSources.DataBindingSource.Switch.Level=SourceLevels.Error;
            string prefs=Path.Combine(artifacts,"test-preferences-"+Guid.NewGuid().ToString("N")+".json");
            // Deliberately no UI window: validate the real pane/XAML against stub host services.
            var pane=new IllumDockPane(Proxy<ILocationService>.Create(),Proxy<IMapViewController>.Create(),Proxy<IElevationManager>.Create(),prefs);
            pane.Start(); pane.Latitude="38.89"; pane.Longitude="-77.04"; pane.FirstDate=new DateTime(2026,10,29);
            pane.StandardIndex=0; pane.MissionIndex=0; pane.ZoneIndex=0; pane.Terrain=false;
            pane.CalculateCommand.Execute(null);
            Pump(()=>pane.Rows.Count==30 || pane.Status.StartsWith("Calculation failed"));
            Assert(pane.Rows.Count==30,"30-night pane calculation: "+pane.Status);
            Assert(pane.SelectedReport!=null && pane.SelectedReport.level==Illum.Core.NightReport.LightLevel.MIXED,"Selected night rating: "+pane.SelectedReport?.level+"; "+pane.Status+"; "+pane.Headline);
            Assert(pane.Detail.Contains("EENT") && pane.Detail.Contains("Moonrise") && pane.ZoneDescription.Contains("America/New_York"),"Rendered labels and local zone");
            var view=new IllumView { DataContext=pane, Width=520,Height=1600 };
            Render(view,520,1600,Path.Combine(artifacts,"Illum-preview.png"));
            var planningGrid=Descendants(view).OfType<DataGrid>().Single();
            Assert(planningGrid.Columns.Count==4 && planningGrid.Columns.All(c=>c.ActualWidth>0),"Four planning columns have usable widths");
            ValidateContrast(view); ValidateMoonPhases();
            Assert(pane.KeyTimes.Count==8 && pane.MoonSummary.Contains("Gibbous"),"Moon summary, peak light and key event cards");
            Assert(pane.Rows.All(r=>!string.IsNullOrEmpty(r.RiseSet)),"Rise/set summary in each planning row");
            var chart=Descendants(view).OfType<TimelineView>().Single();
            Assert(ReferenceEquals(chart.Settings,pane.SelectedSettings),"Timeline uses the selected standard and mission");
            var scroller=Descendants(view).OfType<ScrollViewer>().First(); scroller.ScrollToBottom();
            Render(view,520,1600,Path.Combine(artifacts,"Illum-plan-preview.png")); scroller.ScrollToTop();
            var controls=new StackPanel { Width=420,Background=(Brush)view.FindResource("IllumPanel") };
            controls.Resources.MergedDictionaries.Add(view.Resources);
            controls.Children.Add(new TextBlock { Text="Dropdown items and calendar", Margin=new Thickness(12) });
            controls.Children.Add(new ComboBoxItem { Content="Average clouds",Style=(Style)view.FindResource(typeof(ComboBoxItem)) });
            controls.Children.Add(new ComboBoxItem { Content="Ground concealment",IsSelected=true,Style=(Style)view.FindResource(typeof(ComboBoxItem)) });
            controls.Children.Add(new Calendar { Style=(Style)view.FindResource("IllumCalendar"),DisplayDate=new DateTime(2026,10,29),SelectedDate=new DateTime(2026,10,29) });
            Render(controls,420,350,Path.Combine(artifacts,"Illum-controls-preview.png"));
            pane.SelectedRow=pane.Rows[5]; Assert(ReferenceEquals(pane.SelectedReport,pane.Rows[5].Report),"Grid row selection");
            pane.NextNightCommand.Execute(null); Assert(ReferenceEquals(pane.SelectedReport,pane.Rows[6].Report),"Next night within the existing plan");
            pane.PreviousNightCommand.Execute(null); long selected=pane.SelectedReport.epochDay;
            pane.SkyIndex=1; pane.CalculateCommand.Execute(null); AwaitPlan(pane,30);
            Assert(pane.SelectedReport.epochDay==selected,"Settings recalculation preserves selected night");
            pane.Latitude="NaN"; pane.CalculateCommand.Execute(null);
            Assert(pane.Status.StartsWith("Latitude must") && pane.SelectedReport==null && pane.Rows.Count==0 && pane.KeyTimes.Count==0 && pane.Notes=="","Invalid coordinate clears the old rating, chart and details");
            pane.Latitude="45.815"; pane.Longitude="15.982"; pane.FirstDate=new DateTime(2026,10,24); pane.Nights="7";
            pane.MissionIndex=1; pane.CalculateCommand.Execute(null); Pump(()=>pane.Rows.Count==7 || pane.Status.StartsWith("Calculation failed"));
            Assert(pane.Rows.Count==7 && pane.ZoneDescription.Contains("Europe/Zagreb"),"Location and range update");
            Assert(pane.Detail.Contains("Clocks go back") && pane.Notes.Contains("Clocks go back"),"Civil DST transition note in the main report");
            pane.Nights="90"; pane.CalculateCommand.Execute(null);
            pane.Nights="7"; pane.Latitude="-33.87"; pane.Longitude="151.21"; pane.CalculateCommand.Execute(null);
            Pump(()=>pane.Rows.Count==7 || pane.Status.StartsWith("Calculation failed"));
            Assert(pane.ZoneDescription.Contains("Australia/Sydney") && pane.Rows.Count==7,"Superseded calculation cannot overwrite the latest plan");
            pane.Dispose();
            var restored=new IllumDockPane(Proxy<ILocationService>.Create(),Proxy<IMapViewController>.Create(),Proxy<IElevationManager>.Create(),prefs);
            Assert(restored.Latitude=="-33.87" && restored.Nights=="7" && restored.MissionIndex==1,"Saved options round trip"); restored.Dispose();
            File.Copy(prefs,prefs+".bak",true); File.WriteAllText(prefs,"invalid json");
            restored=new IllumDockPane(Proxy<ILocationService>.Create(),Proxy<IMapViewController>.Create(),Proxy<IElevationManager>.Create(),prefs);
            Assert(restored.Latitude=="-33.87" && restored.Status.StartsWith("Recovered"),"Corrupt preferences recover the atomic backup"); restored.Dispose();
            File.WriteAllText(prefs,"{\"Values\":[{\"Key\":\"MissionIndex\",\"Value\":\"999\"},{\"Key\":\"ZoneIndex\",\"Value\":\"-1\"}]}");
            restored=new IllumDockPane(Proxy<ILocationService>.Create(),Proxy<IMapViewController>.Create(),Proxy<IElevationManager>.Create(),prefs);
            Assert(restored.MissionIndex==0 && restored.ZoneIndex==0,"Invalid saved selections use valid defaults"); restored.Dispose();
            ValidateInteraction(artifacts); ValidateFailurePaths(artifacts); ValidateUsability(artifacts); ValidateStableLayout(artifacts);
            listener.Flush(); Assert(errors.ToString().Length==0,"WPF binding errors: "+errors);
            app.Shutdown();
            Console.WriteLine("PASS: WPF rendering/bindings, contrast/moon phases, 300-760 px four-column layouts, 90-night scrolling, routed keyboard navigation/calculation/Escape, inline validation, date selection, map adapters/cleanup, IANA/DST/fallback, terrain and preferences. Host services were stubbed.");
        }
        private static void AwaitPlan(IllumDockPane pane,int count)
        {
            Pump(()=>pane.Rows.Count==count || pane.Status.StartsWith("Calculation failed"));
            Assert(pane.Rows.Count==count,"Plan calculation: "+pane.Status);
        }
        private static IllumDockPane TestPane(string artifacts,ILocationService gps=null,IMapViewController map=null,IElevationManager elevation=null,Func<TzMap> tz=null,string preferencesPath=null)
        {
            var p=new IllumDockPane(gps??Proxy<ILocationService>.Create(),map??Proxy<IMapViewController>.Create(),elevation??Proxy<IElevationManager>.Create(),preferencesPath??Path.Combine(artifacts,"test-preferences-"+Guid.NewGuid().ToString("N")+".json"),tz);
            p.Start(); p.Latitude="38.89"; p.Longitude="-77.04"; p.FirstDate=new DateTime(2026,10,29); p.Nights="7"; p.Terrain=false;
            return p;
        }
        private static void ValidateInteraction(string artifacts)
        {
            int pushes=0,pops=0; var handlers=new Dictionary<string,Delegate>();
            var map=Proxy<IMapViewController>.Create(call=> {
                string name=call.MethodName;
                if(name=="PushMapEvents") pushes++; else if(name=="PopMapEvents") pops++;
                else if(name.StartsWith("add_")) { string key=name.Substring(4); handlers.TryGetValue(key,out var previous); handlers[key]=Delegate.Combine(previous,(Delegate)call.Args[0]); }
                else if(name.StartsWith("remove_")) { string key=name.Substring(7); handlers.TryGetValue(key,out var previous); handlers[key]=Delegate.Remove(previous,(Delegate)call.Args[0]); }
                return null;
            });
            var gps=Proxy<ILocationService>.Create(call=>call.MethodName=="GetGpsPosition"?new TAKEngine.Core.GeoPoint(45.815,15.982):null);
            using(var pane=TestPane(artifacts,gps,map))
            {
                pane.GpsCommand.Execute(null); AwaitPlan(pane,7);
                Assert(pane.Latitude=="45.815000" && pane.FirstDate==new DateTime(2026,10,29),"GPS updates position while preserving the chosen evening");
                pane.PreviousNightCommand.Execute(null); AwaitPlan(pane,7);
                Assert(pane.FirstDate==new DateTime(2026,10,28) && pane.SelectedReport.epochDay==CivilDate.fromCivil(2026,10,28),"Previous night beyond the plan shifts its first evening");
                pane.PickCommand.Execute(null);
                Assert(pushes==1 && handlers["MapClick"]!=null && handlers["ItemClick"]!=null && handlers["MapLongPress"]!=null,"Map pick subscribes all point gestures");
                pane.PickCommand.Execute(null);
                Assert(pops==1 && handlers.Values.All(v=>v==null),"Cancel pick restores host events");
                pane.PickCommand.Execute(null);
                var marker=new NamedMarker("illum-sdk-check") { Position=new TAKEngine.Core.GeoPoint(46.1,16.2), Name="Ridge observation point" };
                var mouse=new MouseEventArgs(Mouse.PrimaryDevice,Environment.TickCount);
                var click=new MapMouseEventArgs(mouse,new Point(0,0),marker,new TAKEngine.Core.GeoPoint(46.3,16.4));
                handlers["ItemClick"].DynamicInvoke(null,click); AwaitPlan(pane,7);
                Assert(pane.Latitude=="46.100000" && pane.Longitude=="16.200000" && pops==2,"Picking a displaced marker symbol uses its exact position and restores events");
                Assert(pane.LocationSummary.Contains("Ridge observation point") && pane.LocationSummary.Contains("46.100000° N"),"Picked marker name and coordinates are visible");
                pane.PickCommand.Execute(null);
                click=new MapMouseEventArgs(mouse,new Point(0,0),null,new TAKEngine.Core.GeoPoint(47.3,17.4));
                handlers["MapLongPress"].DynamicInvoke(null,click); AwaitPlan(pane,7);
                Assert(pane.Latitude=="47.300000" && pops==3,"Long press uses the clicked ground point");
                pane.TonightCommand.Execute(null); AwaitPlan(pane,7);
                Assert(pane.SelectedReport.epochDay==NightCalculator.nightOf(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),17.4),"Tonight resets a manual planning date");
                pane.PickCommand.Execute(null); pane.Dispose();
                Assert(pushes==pops && handlers.Values.All(v=>v==null),"Plugin unload restores host event stack");
            }
        }
        private static void ValidateUsability(string artifacts)
        {
            string prefs=Path.Combine(artifacts,"test-preferences-"+Guid.NewGuid().ToString("N")+".json");
            using(var pane=TestPane(artifacts,preferencesPath:prefs))
            {
                pane.Nights="90"; pane.CalculateCommand.Execute(null); AwaitPlan(pane,90);
                var view=new IllumView { DataContext=pane,Width=520,Height=900 };
                Render(view,520,900);
                var grid=(DataGrid)view.FindName("PlanningGrid"); var scroll=(ScrollViewer)view.FindName("ReportScroll");
                Assert(grid.Columns.Select(c=>c.Header.ToString()).SequenceEqual(new[]{"Evening","Light","HLL %","Moon"}),"Compact grid columns");
                foreach(int width in new[]{300,380,520,760})
                {
                    view.Width=width; Render(view,width,900);
                    Assert(grid.Columns.All(c=>c.ActualWidth>40) && grid.Columns.Sum(c=>c.ActualWidth)<=grid.ActualWidth+1,"Grid columns fit at "+width+" px");
                    var inner=Descendants(grid).OfType<ScrollViewer>().First();
                    Assert(inner.ComputedHorizontalScrollBarVisibility!=Visibility.Visible && inner.ScrollableWidth<1 && inner.ScrollableHeight<1,"Grid has no hidden overflow or nested scrolling at "+width+" px");
                    foreach(var text in Descendants(grid).OfType<TextBlock>().Where(t=>!string.IsNullOrEmpty(t.Text)))
                    {
                        var measured=new FormattedText(text.Text,System.Globalization.CultureInfo.CurrentCulture,text.FlowDirection,new Typeface(text.FontFamily,text.FontStyle,text.FontWeight,text.FontStretch),text.FontSize,text.Foreground,VisualTreeHelper.GetDpi(text).PixelsPerDip);
                        Assert(measured.Width<=text.ActualWidth+1,"Unclipped grid text at "+width+" px: "+text.Text+" ("+measured.Width+" / "+text.ActualWidth+")");
                    }
                    scroll.ScrollToVerticalOffset(scroll.VerticalOffset+grid.TranslatePoint(new Point(),scroll).Y-60);
                    Render(view,width,900,Path.Combine(artifacts,"Illum-grid-"+width+".png"));
                }
                view.Width=380; Render(view,380,900); grid.SelectedIndex=2;
                RaiseKey(grid,Key.Down); Render(view,380,900);
                Assert(grid.SelectedIndex==3 && ReferenceEquals(pane.SelectedRow,grid.SelectedItem) && scroll.VerticalOffset>0,"Down selects the next report without jumping to settings");
                RaiseKey(grid,Key.PageDown); Assert(grid.SelectedIndex>3,"Page Down advances through nights");
                RaiseKey(grid,Key.End); Assert(grid.SelectedIndex==89,"End selects the final night");
                RaiseKey(grid,Key.Home); Assert(grid.SelectedIndex==0,"Home selects the first night");
                grid.Items.SortDescriptions.Add(new SortDescription(nameof(NightRow.IllumPercent),ListSortDirection.Descending));
                RaiseKey(grid,Key.Home);
                Assert(ReferenceEquals(pane.SelectedRow,grid.Items[0]) && pane.SelectedRow.IllumPercent==pane.Rows.Max(r=>r.IllumPercent),"Keyboard navigation follows the displayed sort order");
                grid.Items.SortDescriptions.Clear(); grid.SelectedIndex=0;
                scroll.ScrollToVerticalOffset(scroll.VerticalOffset+grid.TranslatePoint(new Point(),scroll).Y);
                Render(view,380,900); double before=scroll.VerticalOffset;
                var wheel=new MouseWheelEventArgs(Mouse.PrimaryDevice,Environment.TickCount,-120) { RoutedEvent=Mouse.PreviewMouseWheelEvent };
                grid.RaiseEvent(wheel); Render(view,380,900);
                Assert(wheel.Handled && scroll.VerticalOffset>before,"Wheel over the grid scrolls the outer pane");
                RaiseKey(grid,Key.Enter); Render(view,380,900);
                var banner=(Border)view.FindName("ReportBanner");
                Assert(Math.Abs(banner.TranslatePoint(new Point(),scroll).Y)<2,"Enter opens the selected report instead of recalculating");
                Assert(KeyboardNavigation.GetTabNavigation(grid)==KeyboardNavigationMode.Once,"Tab leaves the grid in one step");
                var lat=(TextBox)view.FindName("LatitudeInput"); lat.Text="not a latitude"; RaiseKey(lat,Key.Enter); Render(view,380,900);
                Assert(pane.HasErrors && pane.ErrorField==nameof(pane.Latitude) && Validation.GetHasError(lat) && pane.SelectedReport==null,"Enter validates and associates the error with its field");
                Assert(Descendants(view).OfType<TextBlock>().Any(t=>t.Visibility==Visibility.Visible && t.Text.StartsWith("Latitude must")),"Inline latitude error is rendered");
                Render(view,380,900,Path.Combine(artifacts,"Illum-validation-preview.png"));
                lat.Text="38.89"; RaiseKey(lat,Key.Enter); AwaitPlan(pane,90); Render(view,380,900);
                Assert(!pane.HasErrors && !Validation.GetHasError(lat),"Corrected input clears inline validation");
                pane.WindowIndex=2; pane.CustomStart="25:00"; pane.CalculateCommand.Execute(null); Render(view,380,900);
                Assert(pane.CustomOptionsExpanded && pane.ErrorField==nameof(pane.CustomStart) && Validation.GetHasError((TextBox)view.FindName("StartInput")),"Invalid custom input opens its collapsed section");
                pane.WindowIndex=0; pane.CustomStart="22:00"; pane.CustomOptionsExpanded=false; pane.CalculateCommand.Execute(null); AwaitPlan(pane,90);
                var combo=(ComboBox)view.FindName("SkyInput"); var report=pane.SelectedReport;
                Assert(!RaiseKey(combo,Key.Enter).Handled && ReferenceEquals(report,pane.SelectedReport),"Dropdown Enter remains available for normal selection");
                var date=(DatePicker)view.FindName("FirstDateInput"); date.Text="not a date"; Render(view,380,900);
                var calculate=Descendants(view).OfType<Button>().Single(b=>Equals(b.Content,"Calculate plan"));
                calculate.RaiseEvent(new RoutedEventArgs(Button.ClickEvent)); Render(view,380,900);
                Assert(pane.SelectedReport==null && pane.ErrorField==nameof(pane.FirstDate),"Invalid date cannot silently calculate using the previous date");
                date.SelectedDate=new DateTime(2026,10,29); calculate.RaiseEvent(new RoutedEventArgs(Button.ClickEvent)); AwaitPlan(pane,90);
                pane.PickCommand.Execute(null); Render(view,380,900,Path.Combine(artifacts,"Illum-picking-preview.png"));
                Assert(pane.IsPicking && ((Border)view.FindName("PickIndicator")).Visibility==Visibility.Visible,"Persistent pick indicator is visible");
                var indicator=(Border)view.FindName("PickIndicator"); scroll.ScrollToBottom(); Render(view,380,900);
                Assert(indicator.TranslatePoint(new Point(),view).Y>=0 && indicator.TranslatePoint(new Point(0,indicator.ActualHeight),view).Y<=view.ActualHeight,"Pick indicator stays visible while the pane scrolls");
                var escape=new KeyEventArgs(Keyboard.PrimaryDevice,new TestPresentationSource(),Environment.TickCount,Key.Escape) { RoutedEvent=Keyboard.PreviewKeyDownEvent };
                InputManager.Current.ProcessInput(escape);
                Assert(!pane.IsPicking,"Escape cancels picking when keyboard input comes from outside the pane");
                pane.PickCommand.Execute(null); RaiseKey(lat,Key.Escape); Assert(!pane.IsPicking,"Escape also cancels picking from the pane");
                Assert(!RaiseKey(lat,Key.Escape).Handled,"Escape is left to WinTAK outside pick mode");
                pane.PickCommand.Execute(null); view.RaiseEvent(new RoutedEventArgs(FrameworkElement.UnloadedEvent));
                Assert(!pane.IsPicking,"Unloading the pane cancels picking and releases the keyboard hook");
                pane.CustomOptionsExpanded=true; pane.TwilightExpanded=true; pane.LegendExpanded=true;
                // Save on close/unload without requiring another calculation.
            }
            using(var restored=new IllumDockPane(Proxy<ILocationService>.Create(),Proxy<IMapViewController>.Create(),Proxy<IElevationManager>.Create(),prefs))
                Assert(restored.CustomOptionsExpanded && restored.TwilightExpanded && restored.LegendExpanded,"Expanded sections survive restart");
        }
        private static KeyEventArgs RaiseKey(UIElement target,Key key)
        {
            var e=new KeyEventArgs(Keyboard.PrimaryDevice,PresentationSource.FromVisual(target)??new TestPresentationSource(),Environment.TickCount,key) { RoutedEvent=Keyboard.PreviewKeyDownEvent };
            target.RaiseEvent(e); return e;
        }
        private static void ValidateStableLayout(string artifacts)
        {
            var elevation=Proxy<IElevationManager>.Create(call=>call.MethodName=="GetElevation"?(object)(Math.Abs((double)call.Args[0]-38.89)<.000001 && Math.Abs((double)call.Args[1]+77.04)<.000001?0.0:20.0):null);
            using(var pane=TestPane(artifacts,elevation:elevation))
            {
                pane.Nights="90"; pane.Terrain=true; pane.TwilightExpanded=true; pane.CalculateCommand.Execute(null); AwaitPlan(pane,90);
                var view=new IllumView { DataContext=pane,Width=380,Height=720 };
                var grid=(DataGrid)view.FindName("PlanningGrid"); var scroll=(ScrollViewer)view.FindName("ReportScroll");
                var content=(StackPanel)view.FindName("PaneContent"); var banner=(Border)view.FindName("ReportBanner");
                var shadowArea=(Border)view.FindName("ShadowNotesArea"); var shadow=(ScrollViewer)view.FindName("ShadowScroll");
                var cards=(ScrollViewer)view.FindName("KeyTimesScroll"); var twilight=(ScrollViewer)view.FindName("TwilightScroll");
                NightRow longShadow=null; double maxShadowOverflow=0; var cardCounts=new HashSet<int>(); var shadowLengths=new HashSet<int>();
                foreach(int width in new[]{300,380,520})
                {
                    view.Width=width; Layout(view,width,720);
                    double gridY=grid.TranslatePoint(new Point(),content).Y;
                    double bannerHeight=banner.ActualHeight,cardsHeight=cards.ActualHeight,shadowHeight=shadowArea.ActualHeight,twilightHeight=twilight.ActualHeight;
                    foreach(var row in pane.Rows)
                    {
                        pane.SelectedRow=row; Layout(view,width,720);
                        cardCounts.Add(pane.KeyTimes.Count); shadowLengths.Add(pane.ShadowText.Length);
                        Assert(Math.Abs(grid.TranslatePoint(new Point(),content).Y-gridY)<.5,"Grid content position stays fixed across 90 terrain nights at "+width+" px");
                        Assert(banner.ActualHeight==bannerHeight && cards.ActualHeight==cardsHeight && shadowArea.ActualHeight==shadowHeight && twilight.ActualHeight==twilightHeight,"Variable report sections retain their height at "+width+" px");
                        if(shadow.ScrollableHeight>maxShadowOverflow) { maxShadowOverflow=shadow.ScrollableHeight; longShadow=row; }
                    }
                }
                Assert(cardCounts.Count>1 && shadowLengths.Count>10 && maxShadowOverflow>0,"Fixture covers varying terrain cards and long/short shadow text");
                view.Width=380; Layout(view,380,720); grid.SelectedIndex=5; Layout(view,380,720);
                var anchor=(DataGridRow)grid.ItemContainerGenerator.ContainerFromIndex(5);
                scroll.ScrollToVerticalOffset(scroll.VerticalOffset+anchor.TranslatePoint(new Point(),scroll).Y-80); Layout(view,380,720);
                double rowY=anchor.TranslatePoint(new Point(),scroll).Y,offset=scroll.VerticalOffset;
                foreach(int index in new[]{6,7,5,8})
                {
                    grid.SelectedIndex=index; Layout(view,380,720);
                    Assert(Math.Abs(anchor.TranslatePoint(new Point(),scroll).Y-rowY)<.5 && Math.Abs(scroll.VerticalOffset-offset)<.5,"Mouse-style selection keeps visible grid rows at the same screen position");
                }
                RaiseKey(grid,Key.Down); Layout(view,380,720);
                Assert(grid.SelectedIndex==9 && Math.Abs(anchor.TranslatePoint(new Point(),scroll).Y-rowY)<.5,"Visible arrow selection preserves the grid position");
                // Exercise the fallback anchor with a height change outside the bounded text areas.
                grid.SelectedIndex=10; double height=banner.Height; banner.Height+=37; Layout(view,380,720);
                Assert(Math.Abs(anchor.TranslatePoint(new Point(),scroll).Y-rowY)<.5 && Math.Abs(scroll.VerticalOffset-offset-37)<.5,"Viewport anchoring compensates for other height changes above the grid");
                grid.SelectedIndex=11; banner.Height=height; grid.SelectedIndex=12; grid.SelectedIndex=13; Layout(view,380,720);
                Assert(Math.Abs(anchor.TranslatePoint(new Point(),scroll).Y-rowY)<.5,"Rapid date changes share one viewport anchor");
                scroll.ScrollToVerticalOffset(scroll.VerticalOffset+anchor.TranslatePoint(new Point(),scroll).Y+8); Layout(view,380,720);
                double partialRowY=anchor.TranslatePoint(new Point(),scroll).Y;
                Assert(partialRowY<0 && partialRowY+anchor.ActualHeight>0,"Fixture includes a partially visible grid row");
                anchor.BringIntoView(); Layout(view,380,720);
                Assert(Math.Abs(anchor.TranslatePoint(new Point(),scroll).Y-partialRowY)<.5,"Native focus requests do not move an already visible row: "+partialRowY+" -> "+anchor.TranslatePoint(new Point(),scroll).Y);
                RaiseKey(grid,Key.End); Layout(view,380,720); AssertRowVisible(grid,scroll,89);
                RaiseKey(grid,Key.Home); Layout(view,380,720); AssertRowVisible(grid,scroll,0);
                grid.SelectedIndex=1; RaiseKey(grid,Key.Enter); Layout(view,380,720);
                Assert(Math.Abs(banner.TranslatePoint(new Point(),scroll).Y)<2,"Explicit report access cancels a queued grid anchor");
                pane.SelectedRow=longShadow; Layout(view,380,720);
                scroll.ScrollToVerticalOffset(scroll.VerticalOffset+shadowArea.TranslatePoint(new Point(),scroll).Y-20); Layout(view,380,720);
                shadow.ScrollToTop(); Layout(view,380,720); offset=scroll.VerticalOffset;
                RaiseWheel(shadow,-120); Layout(view,380,720);
                Assert(shadow.VerticalOffset>0 && Math.Abs(scroll.VerticalOffset-offset)<.5,"Long shadow details scroll without moving the outer pane");
                shadow.ScrollToTop(); Layout(view,380,720); offset=scroll.VerticalOffset;
                RaiseWheel(shadow,120); Layout(view,380,720);
                Assert(scroll.VerticalOffset<offset,"Wheel returns to the outer pane at the shadow detail boundary");
                Render(view,380,720,Path.Combine(artifacts,"Illum-stable-shadow-preview.png"));
                pane.Latitude="69.65"; pane.Longitude="18.96"; pane.FirstDate=new DateTime(2027,4,1); pane.Terrain=false;
                pane.CalculateCommand.Execute(null); AwaitPlan(pane,90); Layout(view,380,720);
                double polarGridY=grid.TranslatePoint(new Point(),content).Y;
                foreach(var row in pane.Rows)
                {
                    pane.SelectedRow=row; Layout(view,380,720);
                    Assert(Math.Abs(grid.TranslatePoint(new Point(),content).Y-polarGridY)<.5,"Polar/no-window reports retain the same layout");
                }
                Assert(pane.Rows.Any(r=>r.Report.hasWindow()) && pane.Rows.Any(r=>!r.Report.hasWindow()),"Polar fixture spans rated and unrated nights");
            }
            Console.WriteLine("PASS: fixed shadow/notes, banner, event cards and expanded twilight; 90-night terrain/polar layout stability; pixel-stable grid anchoring, visible/offscreen keyboard navigation and detail wheel handoff.");
        }
        private static void AssertRowVisible(DataGrid grid,ScrollViewer scroll,int index)
        {
            var row=(DataGridRow)grid.ItemContainerGenerator.ContainerFromIndex(index); double y=row.TranslatePoint(new Point(),scroll).Y;
            Assert(grid.SelectedIndex==index && y>=-.5 && y+row.ActualHeight<=scroll.ViewportHeight+.5,"Offscreen keyboard navigation reveals only the destination row");
        }
        private static void RaiseWheel(UIElement target,int delta)
        {
            var e=new MouseWheelEventArgs(Mouse.PrimaryDevice,Environment.TickCount,delta) { RoutedEvent=Mouse.PreviewMouseWheelEvent };
            target.RaiseEvent(e);
            if(!e.Handled) { e.RoutedEvent=Mouse.MouseWheelEvent; target.RaiseEvent(e); }
        }
        private sealed class TestPresentationSource : PresentationSource
        {
            public override Visual RootVisual { get; set; }
            public override bool IsDisposed=>false;
            protected override CompositionTarget GetCompositionTargetCore()=>null;
        }
        // A label normally needs the host's native renderer. Keep name storage isolated in this windowless fixture.
        private sealed class NamedMarker : WinTak.Graphics.MapMarker
        {
            internal NamedMarker(string uid):base(uid) { }
            public override string Name { get; set; }
        }
        private static void ValidateFailurePaths(string artifacts)
        {
            int loads=0;
            using(var pane=TestPane(artifacts,tz:()=> { loads++; throw new InvalidDataException("test timezone failure"); }))
            {
                pane.ZoneIndex=1; pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(loads==0 && pane.DisplayZone.isUtc(),"Zulu calculations do not depend on the timezone grid");
                pane.ZoneIndex=0; pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(loads==1 && pane.Notes.Contains("Timezone map unavailable") && pane.ZoneDescription.Contains("nautical"),"Timezone grid failure uses a labelled nautical fallback");
                pane.CalculateCommand.Execute(null); AwaitPlan(pane,7); Assert(loads==1,"Failed timezone grid is not retried every calculation");
                pane.ZoneIndex=4; pane.ManualOffset="-840"; pane.WindowIndex=2; pane.CustomStart="2200L"; pane.CustomEnd="0400";
                pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(pane.DisplayZone.offsetMillis(pane.SelectedReport.refTime)==-14*3600000L,"ATAK manual offset limit and compact custom clock input");
                pane.StandardIndex=3; pane.CustomMillilux="1001"; pane.CalculateCommand.Execute(null);
                Assert(pane.SelectedReport==null && pane.Status.StartsWith("Custom illuminance"),"Custom lux uses ATAK limits and clears a stale plan");
                pane.CustomMillilux="2.2"; pane.StandardIndex=2; pane.WindowIndex=0; pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(pane.Notes.Contains("Rating uses moon illumination and elevation"),"Army moon-rule explanation");
                pane.Latitude="69.65"; pane.Longitude="18.96"; pane.FirstDate=new DateTime(2027,6,21); pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(!pane.SelectedReport.hasWindow() && pane.Notes.Contains("no EENT/BMNT") && pane.ShadowText.Contains("No rated window"),"Polar daylight is explained without shadow results");
            }
            var missing=Proxy<IElevationManager>.Create(call=>call.MethodName=="GetElevation"?(object)double.NaN:null);
            using(var pane=TestPane(artifacts,elevation:missing))
            {
                pane.Terrain=true; pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(!pane.SelectedReport.terrainApplied && pane.Notes.Contains("No terrain data"),"Missing elevation explains the flat-terrain fallback");
            }
            int samples=0;
            var partial=Proxy<IElevationManager>.Create(call=> {
                if(call.MethodName!="GetElevation") return null;
                samples++; if(samples%4==0) throw new IOException("test elevation failure");
                return Math.Abs((double)call.Args[0]-38.89)<.000001 && Math.Abs((double)call.Args[1]+77.04)<.000001 ? 0.0 : 20.0;
            });
            using(var pane=TestPane(artifacts,elevation:partial))
            {
                pane.Terrain=true; pane.CalculateCommand.Execute(null); AwaitPlan(pane,7);
                Assert(pane.SelectedReport.terrainApplied && pane.Notes.Contains("elevation queries failed") && pane.Notes.Contains("Terrain coverage is") && pane.Doctrine.Contains("Flat-terrain doctrine"),"Partial elevation retains terrain, coverage warning and doctrine comparison");
                Assert(pane.KeyTimes.Any(t=>t.Name=="Clears terrain" || t.Name=="Behind terrain"),"Terrain clearance events are visible as key-time cards");
            }
        }
        private static void Pump(Func<bool> done)
        {
            var frame=new DispatcherFrame(); var watch=Stopwatch.StartNew();
            var timer=new DispatcherTimer(TimeSpan.FromMilliseconds(20),DispatcherPriority.Background,(s,e)=> { if(done()||watch.ElapsedMilliseconds>15000) frame.Continue=false; },Dispatcher.CurrentDispatcher);
            Dispatcher.PushFrame(frame); timer.Stop(); Assert(done(),"Pane calculation timed out");
        }
        private static void Assert(bool condition,string message) { if(!condition) throw new Exception(message); }
        private static RenderTargetBitmap Render(FrameworkElement view,int width,int height,string path=null)
        {
            Layout(view,width,height);
            var bitmap=new RenderTargetBitmap(width,height,96,96,PixelFormats.Pbgra32); bitmap.Render(view);
            if(path!=null)
            {
                var encoder=new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
                using(var stream=File.Create(path)) encoder.Save(stream);
            }
            return bitmap;
        }
        private static void Layout(FrameworkElement view,int width,int height)
        {
            view.Measure(new Size(width,height)); view.Arrange(new Rect(0,0,width,height)); view.UpdateLayout();
            // Star-column allocation and validation bindings settle on the dispatcher.
            Dispatcher.CurrentDispatcher.Invoke(DispatcherPriority.ContextIdle,(Action)(()=>{})); view.UpdateLayout();
        }
        private static IEnumerable<DependencyObject> Descendants(DependencyObject node)
        {
            for(int i=0;i<VisualTreeHelper.GetChildrenCount(node);i++)
            { var child=VisualTreeHelper.GetChild(node,i); yield return child; foreach(var descendant in Descendants(child)) yield return descendant; }
        }
        private static void ValidateContrast(FrameworkElement view)
        {
            int checkedControls=0;
            foreach(var control in Descendants(view).OfType<Control>().Where(c=>c is Button || c is ComboBox || c is TextBox))
                if(control.Foreground is SolidColorBrush fg && control.Background is SolidColorBrush bg && bg.Color.A==255)
                { Assert(Contrast(fg.Color,bg.Color)>=4.5,control.GetType().Name+" contrast must remain readable under inherited host styles"); checkedControls++; }
            Assert(checkedControls>=10,"Buttons, dropdowns, coordinate and date input contrast checked");
        }
        private static double Contrast(Color a,Color b)
        {
            double L(Color c) { double V(byte v) { double s=v/255.0; return s<=.04045?s/12.92:Math.Pow((s+.055)/1.055,2.4); } return .2126*V(c.R)+.7152*V(c.G)+.0722*V(c.B); }
            double x=L(a),y=L(b); return (Math.Max(x,y)+.05)/(Math.Min(x,y)+.05);
        }
        private static void ValidateMoonPhases()
        {
            byte[] Pixels(double percent,bool waxing=true,double latitude=40)
            {
                var moon=new MoonPhaseView { Width=100,Height=100,Report=new Illum.Core.NightReport { illumPercent=percent,waxing=waxing,latDeg=latitude } };
                var bitmap=Render(moon,100,100); var pixels=new byte[40000]; bitmap.CopyPixels(pixels,400,0); return pixels;
            }
            bool Lit(byte[] pixels,int x,int y) { int p=(y*100+x)*4; return pixels[p]>150 && pixels[p+1]>200 && pixels[p+2]>200 && pixels[p+3]>200; }
            int Count(byte[] pixels) { int count=0; for(int y=0;y<100;y++) for(int x=0;x<100;x++) if(Lit(pixels,x,y)) count++; return count; }
            int full=Count(Pixels(100)); Assert(full>6500,"Full moon renders a lit disk");
            foreach(double phase in new[] {0.0,25,50,75,100})
                Assert(Math.Abs(Count(Pixels(phase))/(double)full-phase/100)<.025,"Moon illuminated area at "+phase+"%");
            foreach(bool waxing in new[] {true,false}) foreach(double latitude in new[] {40.0,-40})
            { var pixels=Pixels(50,waxing,latitude); bool right=waxing!=(latitude<0); Assert(Lit(pixels,75,50)==right && Lit(pixels,25,50)!=right,"Waxing/waning hemisphere orientation"); }
        }
        private static void ValidateRegistrationNames()
        {
            // CommandManager assigns ribbon IDs to FrameworkElement.Name. Docking uses
            // the same WPF naming constraint. Exercise WPF's validator on real metadata.
            int count=0;
            foreach(var type in typeof(IllumDockPane).Assembly.GetTypes())
                foreach(var attribute in type.GetCustomAttributesData())
                    if(attribute.AttributeType.Name=="ButtonAttribute" || attribute.AttributeType.Name=="DockPaneAttribute")
                    {
                        string id=(string)attribute.ConstructorArguments[0].Value;
                        try { var element=new System.Windows.Controls.ContentControl { Name=id }; }
                        catch(ArgumentException e) { throw new Exception(type.FullName+" registration ID is not a valid WPF Name: "+id,e); }
                        count++;
                    }
            Assert(count==2,"Ribbon and pane registration metadata must both be validated.");
        }
        private sealed class Proxy<T> : RealProxy where T:class
        {
            private readonly Func<IMethodCallMessage,object> handler;
            private Proxy(Func<IMethodCallMessage,object> handler):base(typeof(T)) { this.handler=handler; }
            internal static T Create(Func<IMethodCallMessage,object> handler=null)=>(T)new Proxy<T>(handler).GetTransparentProxy();
            public override IMessage Invoke(IMessage msg)
            {
                var call=(IMethodCallMessage)msg; var method=(MethodInfo)call.MethodBase;
                try
                {
                    object result=handler==null?null:handler(call);
                    if(result==null && method.ReturnType!=typeof(void) && method.ReturnType.IsValueType) result=Activator.CreateInstance(method.ReturnType);
                    return new ReturnMessage(result,null,0,call.LogicalCallContext,call);
                }
                catch(Exception e) { return new ReturnMessage(e,call); }
            }
        }
    }
}
