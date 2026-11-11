using System;
using System.ComponentModel;
using System.Globalization;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
namespace Illum.WinTAK
{
    public partial class IllumView : UserControl
    {
        private IllumDockPane pane;
        private ViewportAnchor viewportAnchor;
        private DispatcherOperation pendingViewportRestore;
        private bool restoringViewport, revealSelection;
        public IllumView() { InitializeComponent(); }
        private void PaneDataContextChanged(object sender,DependencyPropertyChangedEventArgs e) => AttachPane();
        private void PaneLoaded(object sender,RoutedEventArgs e) => AttachPane();
        private void PaneUnloaded(object sender,RoutedEventArgs e) { pane?.CancelPickCommand.Execute(null); DetachPane(); }
        private void DetachPane()
        {
            CancelViewportRestore();
            if(pane!=null)
            {
                ((INotifyPropertyChanged)pane).PropertyChanged-=PanePropertyChanged;
                pane.SelectedNightChanging-=NightChanging; pane.SelectedNightChanged-=NightChanged;
            }
            pane=null;
        }
        private void AttachPane()
        {
            DetachPane(); pane=DataContext as IllumDockPane;
            if(pane!=null)
            {
                ((INotifyPropertyChanged)pane).PropertyChanged+=PanePropertyChanged;
                pane.SelectedNightChanging+=NightChanging; pane.SelectedNightChanged+=NightChanged;
            }
        }
        private void PaneSizeChanged(object sender,SizeChangedEventArgs e)
        {
            if(!e.WidthChanged || ReportBanner==null) return;
            CancelViewportRestore();
            // Reserve space by pane width, so changing nights cannot change these sections' height.
            ReportBanner.Height=e.NewSize.Width<380?188:e.NewSize.Width<520?152:112;
            KeyTimesScroll.Height=e.NewSize.Width<380?360:e.NewSize.Width<520?320:280;
        }
        private void NightChanging(object sender,EventArgs e)
        { if(viewportAnchor==null) viewportAnchor=CaptureViewportAnchor(); }
        private void NightChanged(object sender,EventArgs e)
        {
            BannerScroll.ScrollToTop(); KeyTimesScroll.ScrollToTop(); TwilightScroll.ScrollToTop(); ShadowScroll.ScrollToTop();
            if(viewportAnchor!=null && pendingViewportRestore==null)
                pendingViewportRestore=Dispatcher.BeginInvoke(DispatcherPriority.DataBind,(Action)RestoreViewport);
        }
        private ViewportAnchor CaptureViewportAnchor()
        {
            if(ReportScroll.ViewportHeight<=0) return null;
            // Anchor an actual row rather than the grid's top, including when the grid is sorted.
            for(int i=0;i<PlanningGrid.Items.Count;i++)
                if(PlanningGrid.ItemContainerGenerator.ContainerFromIndex(i) is DataGridRow row && InViewport(row))
                    return new ViewportAnchor(row,row.TranslatePoint(new Point(),ReportScroll).Y);
            if(ReportBanner.TranslatePoint(new Point(),ReportScroll).Y>0) return null;
            foreach(var section in new FrameworkElement[] {ReportBanner,KeyTimesScroll,NightTimeline,TwilightDetails,ShadowNotesArea})
                if(InViewport(section)) return new ViewportAnchor(section,section.TranslatePoint(new Point(),ReportScroll).Y);
            return null;
        }
        private bool InViewport(FrameworkElement element)
        {
            if(element.ActualHeight<=0 || element.Visibility!=Visibility.Visible) return false;
            double y=element.TranslatePoint(new Point(),ReportScroll).Y;
            return y<ReportScroll.ViewportHeight && y+element.ActualHeight>0;
        }
        private void RestoreViewport()
        {
            pendingViewportRestore=null;
            var anchor=viewportAnchor;
            if(anchor==null) return;
            restoringViewport=true;
            try
            {
                UpdateLayout();
                if(IsAncestorOf(anchor.Element))
                {
                    double displacement=anchor.Element.TranslatePoint(new Point(),ReportScroll).Y-anchor.Y;
                    if(Math.Abs(displacement)>.1) ReportScroll.ScrollToVerticalOffset(ReportScroll.VerticalOffset+displacement);
                    UpdateLayout();
                }
                if(revealSelection) EnsureSelectedRowVisible();
            }
            finally { viewportAnchor=null; restoringViewport=false; revealSelection=false; }
        }
        private void CancelViewportRestore()
        { pendingViewportRestore?.Abort(); pendingViewportRestore=null; viewportAnchor=null; revealSelection=false; }
        private void ReportBringIntoView(object sender,RequestBringIntoViewEventArgs e)
        {
            var row=Ancestor<DataGridRow>(e.TargetObject);
            if(row!=null && (viewportAnchor!=null || restoringViewport || InViewport(row))) e.Handled=true;
        }
        private void ReportMouseWheel(object sender,MouseWheelEventArgs e) => CancelViewportRestore();
        private sealed class ViewportAnchor
        {
            internal FrameworkElement Element { get; }
            internal double Y { get; }
            internal ViewportAnchor(FrameworkElement element,double y) { Element=element; Y=y; }
        }
        private void PanePropertyChanged(object sender,PropertyChangedEventArgs e)
        {
            if(e.PropertyName!=nameof(IllumDockPane.ErrorField) || pane==null || !pane.HasErrors) return;
            string field=pane.ErrorField;
            var input=Inputs().FirstOrDefault(c=>Equals(c.Tag,field));
            if(input==null) return;
            if(Ancestor<Expander>(input)==CustomOptions) CustomOptions.SetCurrentValue(Expander.IsExpandedProperty,true);
            Dispatcher.BeginInvoke(DispatcherPriority.Input,(Action)(()=> {
                if(pane==null || !pane.HasErrors || pane.ErrorField!=field) return;
                (input.Parent as FrameworkElement??input).BringIntoView(); input.Focus();
                if(input is TextBox text) text.SelectAll();
            }));
        }
        private Control[] Inputs() => new Control[] {LatitudeInput,LongitudeInput,FirstDateInput,NightsInput,StandardInput,MissionInput,SkyInput,WindowInput,ZoneInput,MethodInput,LuxInput,PercentInput,AltitudeInput,StartInput,EndInput,ToleranceInput,OffsetInput};
        private void CalculateClicked(object sender,RoutedEventArgs e) => CalculatePlan();
        private void CalculatePlan()
        {
            if(pane==null) return;
            foreach(var text in Inputs().OfType<TextBox>()) text.GetBindingExpression(TextBox.TextProperty)?.UpdateSource();
            FirstDateInput.ApplyTemplate();
            var editor=FirstDateInput.Template.FindName("PART_TextBox",FirstDateInput) as DatePickerTextBox;
            string dateText=editor?.Text??FirstDateInput.Text;
            if(!DateTime.TryParse(dateText,CultureInfo.CurrentCulture,DateTimeStyles.None,out DateTime date))
            { pane.RejectInput(nameof(IllumDockPane.FirstDate),"Enter a valid evening date from 1900 to 2100."); return; }
            if(date.Year<1900 || date.Year>2100) { pane.RejectInput(nameof(IllumDockPane.FirstDate),"Choose an evening date from 1900 to 2100."); return; }
            FirstDateInput.SelectedDate=date.Date;
            FirstDateInput.GetBindingExpression(DatePicker.SelectedDateProperty)?.UpdateSource();
            pane.CalculateCommand.Execute(null);
        }
        private void DateValidationError(object sender,DatePickerDateValidationErrorEventArgs e)
        {
            e.ThrowException=false;
            ((DatePicker)sender).SetCurrentValue(DatePicker.SelectedDateProperty,null);
            if(pane!=null) pane.FirstDate=null;
            pane?.RejectInput(nameof(IllumDockPane.FirstDate),"Enter a valid evening date from 1900 to 2100.");
        }
        private void PanePreviewKeyDown(object sender,KeyEventArgs e)
        {
            if(pane==null || e.Handled) return;
            Key key=e.Key==Key.System?e.SystemKey:e.Key;
            // Handle pane shortcuts here instead of registering window-wide access keys.
            if(Keyboard.Modifiers==ModifierKeys.Alt)
            {
                switch(key)
                {
                    case Key.C: CalculatePlan(); break;
                    case Key.P: pane.GpsCommand.Execute(null); break;
                    case Key.M: pane.CenterCommand.Execute(null); break;
                    case Key.K: pane.PickCommand.Execute(null); break;
                    case Key.V: pane.PreviousNightCommand.Execute(null); break;
                    case Key.T: pane.TonightCommand.Execute(null); break;
                    case Key.X: pane.NextNightCommand.Execute(null); break;
                    case Key.R: OpenReport(); break;
                    case Key.L: LatitudeInput.Focus(); break;
                    case Key.O: LongitudeInput.Focus(); break;
                    case Key.E: FirstDateInput.Focus(); break;
                    case Key.I: NightsInput.Focus(); break;
                    default: return;
                }
                e.Handled=true; return;
            }
            if(e.Key==Key.Escape && pane.IsPicking) { pane.CancelPickCommand.Execute(null); e.Handled=true; return; }
            if(e.Key!=Key.Enter) return;
            if(Keyboard.Modifiers==ModifierKeys.Control) { CalculatePlan(); e.Handled=true; return; }
            // Preserve the normal Enter behavior of dropdowns, date editors and buttons.
            var origin=e.OriginalSource as DependencyObject;
            if(Keyboard.Modifiers==ModifierKeys.None && Ancestor<TextBox>(origin) is TextBox text && !text.AcceptsReturn && Ancestor<DatePicker>(origin)==null)
            { CalculatePlan(); e.Handled=true; }
        }
        private void GridPreviewKeyDown(object sender,KeyEventArgs e)
        {
            if(e.Handled || Keyboard.Modifiers!=ModifierKeys.None || Ancestor<DataGridColumnHeader>(e.OriginalSource as DependencyObject)!=null) return;
            if(e.Key==Key.Enter) { OpenReport(); e.Handled=true; return; }
            int count=PlanningGrid.Items.Count,index=PlanningGrid.SelectedIndex;
            if(count==0) return;
            int page=Math.Max(1,(int)(ReportScroll.ViewportHeight/PlanningGrid.RowHeight)-1);
            int next=e.Key==Key.Up?index-1:e.Key==Key.Down?index+1:e.Key==Key.Home?0:e.Key==Key.End?count-1:e.Key==Key.PageUp?index-page:e.Key==Key.PageDown?index+page:-1;
            if(e.Key!=Key.Up && e.Key!=Key.Down && e.Key!=Key.Home && e.Key!=Key.End && e.Key!=Key.PageUp && e.Key!=Key.PageDown) return;
            next=Math.Max(0,Math.Min(count-1,next)); PlanningGrid.SelectedIndex=next;
            PlanningGrid.UpdateLayout();
            var row=PlanningGrid.ItemContainerGenerator.ContainerFromIndex(next) as DataGridRow;
            row?.Focus();
            if(viewportAnchor!=null) revealSelection=true; else EnsureSelectedRowVisible();
            e.Handled=true;
        }
        private void EnsureSelectedRowVisible()
        {
            UpdateLayout();
            var row=PlanningGrid.ItemContainerGenerator.ContainerFromIndex(PlanningGrid.SelectedIndex) as DataGridRow;
            if(row==null) return;
            double y=row.TranslatePoint(new Point(),ReportScroll).Y;
            if(y<0) ReportScroll.ScrollToVerticalOffset(ReportScroll.VerticalOffset+y);
            else if(y+row.ActualHeight>ReportScroll.ViewportHeight) ReportScroll.ScrollToVerticalOffset(ReportScroll.VerticalOffset+y+row.ActualHeight-ReportScroll.ViewportHeight);
            UpdateLayout();
        }
        private void GridDoubleClick(object sender,MouseButtonEventArgs e)
        { if(e.ChangedButton==MouseButton.Left && Ancestor<DataGridRow>(e.OriginalSource as DependencyObject)!=null) { OpenReport(); e.Handled=true; } }
        private void GridMouseWheel(object sender,MouseWheelEventArgs e)
        { CancelViewportRestore(); ScrollOuter(e); }
        private void DetailMouseWheel(object sender,MouseWheelEventArgs e)
        {
            var detail=(ScrollViewer)sender;
            if(e.Delta>0 && detail.VerticalOffset>0 || e.Delta<0 && detail.VerticalOffset<detail.ScrollableHeight) return;
            ScrollOuter(e);
        }
        private void ScrollOuter(MouseWheelEventArgs e)
        {
            int lines=SystemParameters.WheelScrollLines;
            double distance=lines<0?ReportScroll.ViewportHeight:lines*16;
            ReportScroll.ScrollToVerticalOffset(ReportScroll.VerticalOffset-e.Delta/120.0*distance);
            e.Handled=true;
        }
        private void OpenReportClicked(object sender,RoutedEventArgs e) => OpenReport();
        private void OpenReport()
        {
            if(pane?.SelectedReport==null) return;
            CancelViewportRestore();
            UpdateLayout();
            ReportScroll.ScrollToVerticalOffset(ReportScroll.VerticalOffset+ReportBanner.TranslatePoint(new Point(),ReportScroll).Y);
            ReportBanner.Focus();
        }
        private static T Ancestor<T>(DependencyObject node) where T:DependencyObject
        {
            while(node!=null)
            {
                if(node is T found) return found;
                node=(node is Visual?VisualTreeHelper.GetParent(node):null)??LogicalTreeHelper.GetParent(node);
            }
            return null;
        }
    }
}
