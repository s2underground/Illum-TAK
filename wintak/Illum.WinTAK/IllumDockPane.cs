using System;
using System.Collections.Generic;
using System.Collections;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.ComponentModel.Composition;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Input;
using System.Windows.Media;
using Illum.Core;
using WinTak.Common.Services;
using WinTak.Display;
using WinTak.Framework;
using WinTak.Framework.Docking;
using WinTak.Framework.Docking.Attributes;
using WinTak.Mapping;
using GeoPoint = TAKEngine.Core.GeoPoint;

namespace Illum.WinTAK
{
    [Export(typeof(IPlugin))]
    [DockPane(Id, "Illum", Content = typeof(IllumView))]
    public sealed class IllumDockPane : DockPane, IPlugin, IDisposable, INotifyDataErrorInfo
    {
        public const string Id = "S2U_Illum_DockPane";
        private static readonly CultureInfo Invariant = CultureInfo.InvariantCulture;
        private readonly ILocationService location;
        private readonly IMapViewController map;
        private readonly IElevationManager elevation;
        private readonly SemaphoreSlim worker = new SemaphoreSlim(1,1);
        private CancellationTokenSource cancellation;
        private TzMap tzMap;
        private readonly Func<TzMap> loadTimezoneMap;
        private bool timezoneMapFailed, manualDate;
        private long? lastPlanFirstDay, selectionOverride;
        private DateTime? firstDate=DateTime.Today;
        private HorizonProfile resultHorizon;
        private string resultWarning="", notes="";
        private bool disposed, picking;
        private int generation;
        private string status = "Choose a location, then calculate a plan.";
        private string headline = "Select a night", detail = "", shadows = "", doctrine = "", zoneDescription = "", standardReference = "";
        private Brush ratingBrush = Brushes.DimGray;
        private NightReport selectedReport;
        private NightRow selectedRow;
        private DisplayZone displayZone = Core.DisplayZone.ZULU;
        private bool resultZulu, resultTerrainRequested;
        private PlanSettings resultSettings;
        private readonly string settingsPath;
        private readonly Dictionary<string,string> inputErrors=new Dictionary<string,string>();
        private string latitude="", longitude="", locationName="Entered coordinates", errorField="";
        private bool settingPosition, loadingSettings, customOptionsExpanded, twilightExpanded, legendExpanded;

        public string Latitude { get=>latitude; set { if(SetProperty(ref latitude,value)) PositionEdited(); } }
        public string Longitude { get=>longitude; set { if(SetProperty(ref longitude,value)) PositionEdited(); } }
        public string LocationName { get=>locationName; set { if(SetProperty(ref locationName,value)) NotifyPropertyChanged(nameof(LocationSummary)); } }
        public string LocationSummary => double.TryParse(Latitude,NumberStyles.Float,Invariant,out double lat) && double.TryParse(Longitude,NumberStyles.Float,Invariant,out double lon) && Finite(lat) && Finite(lon) && Math.Abs(lat)<=90 && Math.Abs(lon)<=180
            ?LocationName+" · "+Math.Abs(lat).ToString("0.000000",Invariant)+(lat<0?"° S":"° N")+", "+Math.Abs(lon).ToString("0.000000",Invariant)+(lon<0?"° W":"° E"):"Choose a location or enter decimal degrees.";
        public bool CustomOptionsExpanded { get=>customOptionsExpanded; set=>SetProperty(ref customOptionsExpanded,value); }
        public bool TwilightExpanded { get=>twilightExpanded; set=>SetProperty(ref twilightExpanded,value); }
        public bool LegendExpanded { get=>legendExpanded; set=>SetProperty(ref legendExpanded,value); }
        public string ErrorField => errorField;
        public bool HasErrors => inputErrors.Count>0;
        public event EventHandler<DataErrorsChangedEventArgs> ErrorsChanged;
        public IEnumerable GetErrors(string propertyName) => propertyName!=null && inputErrors.TryGetValue(propertyName,out string error)?new[]{error}:Array.Empty<string>();
        public DateTime? FirstDate { get=>firstDate; set { if(SetProperty(ref firstDate,value)) manualDate=true; } }
        public string Nights { get; set; } = "30";
        public int StandardIndex { get; set; }
        public int MissionIndex { get; set; }
        public int SkyIndex { get; set; }
        public int WindowIndex { get; set; }
        public int ZoneIndex { get; set; }
        public int CustomMethodIndex { get; set; }
        public string CustomMillilux { get; set; } = "2.2";
        public string CustomPercent { get; set; } = "20";
        public string CustomAltitude { get; set; } = "30";
        public string CustomStart { get; set; } = "22:00";
        public string CustomEnd { get; set; } = "04:00";
        public string TolerancePercent { get; set; } = "10";
        public string ManualOffset { get; set; } = "0";
        public bool ShowZulu { get; set; } = true;
        public bool IncludeNightSky { get; set; }
        public bool Terrain { get; set; }
        public string[] Standards { get; } = { "USAF", "USN/USMC", "Army", "Custom" };
        public string[] Missions { get; } = { "NVG / aviation", "Ground concealment" };
        public string[] Skies { get; } = PlanSettings.Sky.All.Select(x=>x.label).ToArray();
        public string[] Windows { get; } = { "EENT–BMNT", "Sunset–Sunrise", "Custom" };
        public string[] Zones { get; } = { "Local at selected point", "Zulu", "Device", "Military / nautical", "Manual UTC offset" };
        public string[] Methods { get; } = { "Illuminance", "Moon illumination + elevation" };
        public ObservableCollection<NightRow> Rows { get; } = new ObservableCollection<NightRow>();
        public ObservableCollection<TimeCard> KeyTimes { get; } = new ObservableCollection<TimeCard>();
        public PlanSettings SelectedSettings => resultSettings ?? PlanSettings.defaults();
        public string MoonSummary => selectedReport==null?"":selectedReport.illumPercent.ToString("0",Invariant)+"% illuminated · "+selectedReport.phaseName;
        public string BannerSummary => selectedReport?.hasWindow()==true?Formats.percent(selectedReport.hllFraction)+" of "+selectedReport.windowLabel+" ("+Formats.time(selectedReport.windowStart,displayZone)+"–"+Formats.time(selectedReport.windowEnd,displayZone)+") is HLL · "+SelectedSettings.standard.label+(selectedReport.terrainApplied?" · terrain adjusted":""):"";
        public ICommand CalculateCommand { get; }
        public ICommand GpsCommand { get; }
        public ICommand CenterCommand { get; }
        public ICommand PickCommand { get; }
        public ICommand CancelPickCommand { get; }
        public ICommand PreviousNightCommand { get; }
        public ICommand NextNightCommand { get; }
        public ICommand TonightCommand { get; }
        public string PickLabel => picking ? "Cancel pick" : "Pick on map";
        public bool IsPicking => picking;
        public string Status { get=>status; private set=>SetProperty(ref status,value); }
        public string Headline { get=>headline; private set=>SetProperty(ref headline,value); }
        public string Detail { get=>detail; private set=>SetProperty(ref detail,value); }
        public string ShadowText { get=>shadows; private set=>SetProperty(ref shadows,value); }
        public string ShadowSummary => selectedReport==null?"":selectedReport.hasWindow()?selectedReport.shadows.headline():"No rated window for a shadow outlook.";
        public string ShadowIntervals => selectedReport?.hasWindow()==true?string.Join(Environment.NewLine,selectedReport.shadows.lines(DisplayZone)):"";
        public string Doctrine { get=>doctrine; private set=>SetProperty(ref doctrine,value); }
        public string ZoneDescription { get=>zoneDescription; private set=>SetProperty(ref zoneDescription,value); }
        public string StandardReference { get=>standardReference; private set=>SetProperty(ref standardReference,value); }
        public string Notes { get=>notes; private set=>SetProperty(ref notes,value); }
        public Brush RatingBrush { get=>ratingBrush; private set=>SetProperty(ref ratingBrush,value); }
        public NightReport SelectedReport
        {
            get=>selectedReport;
            private set { if(SetProperty(ref selectedReport,value)) { NotifyPropertyChanged(nameof(MoonSummary)); NotifyPropertyChanged(nameof(BannerSummary)); NotifyPropertyChanged(nameof(ShadowSummary)); NotifyPropertyChanged(nameof(ShadowIntervals)); } }
        }
        internal event EventHandler SelectedNightChanging;
        internal event EventHandler SelectedNightChanged;
        public DisplayZone DisplayZone { get=>displayZone; private set=>SetProperty(ref displayZone,value); }
        public NightRow SelectedRow
        {
            get=>selectedRow;
            set { if(SetProperty(ref selectedRow,value) && value!=null) Render(value.Report); }
        }

        [ImportingConstructor]
        public IllumDockPane(ILocationService location, IMapViewController map, IElevationManager elevation)
            : this(location,map,elevation,Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),"WinTAK","Illum","settings.json")) { }

        internal IllumDockPane(ILocationService location, IMapViewController map, IElevationManager elevation,string settingsPath,Func<TzMap> loadTimezoneMap=null)
        {
            this.location=location; this.map=map; this.elevation=elevation; this.settingsPath=settingsPath;
            this.loadTimezoneMap=loadTimezoneMap??TzMap.LoadEmbedded;
            CalculateCommand=new Command(()=>Calculate());
            GpsCommand=new Command(()=>UseGps()); CenterCommand=new Command(()=>UseCenter()); PickCommand=new Command(()=>TogglePick());
            CancelPickCommand=new Command(()=> { if(picking) { EndPick(); Status="Map selection cancelled."; } });
            PreviousNightCommand=new Command(()=>ShiftNight(-1)); NextNightCommand=new Command(()=>ShiftNight(1)); TonightCommand=new Command(()=>UseTonight());
            Closed+=(s,e)=>{ SaveSettings(); CancelCalculation(); EndPick(); };
            Activated+=(s,e)=>{ if(!disposed && !string.IsNullOrWhiteSpace(Latitude)) Calculate(); };
            LoadSettings();
        }
        public void Start() { disposed=false; }
        public void Stop() => Dispose();
        public void Dispose()
        {
            if(disposed) return;
            SaveSettings();
            disposed=true; CancelCalculation();
            void CleanUp() { EndPick(); ClearReport("Plugin stopped"); }
            if(Dispatcher.CheckAccess()) CleanUp(); else Dispatcher.Invoke((Action)CleanUp);
        }
        private void CancelCalculation() { generation++; cancellation?.Cancel(); }
        private void UseGps()
        {
            try { var p=location.GetGpsPosition(); if(!ValidPoint(p)) { Status="No GPS fix is available. Use the map center or enter coordinates."; return; } SetPosition(p,"My position"); }
            catch(Exception e) { Status="Could not read GPS: "+e.Message; }
        }
        private void UseCenter()
        {
            try { var p=map.MapViewCore.Camera.CenterPosition; if(!ValidPoint(p)) { Status="The map center is not available yet."; return; } SetPosition(p,"Map center"); }
            catch(Exception e) { Status="Could not read the map center: "+e.Message; }
        }
        private static bool ValidPoint(GeoPoint p) => p!=null && !double.IsNaN(p.Latitude) && !double.IsNaN(p.Longitude) && Math.Abs(p.Latitude)<=90 && Math.Abs(p.Longitude)<=180;
        private void PositionEdited()
        {
            if(!settingPosition && !loadingSettings) LocationName="Entered coordinates";
            NotifyPropertyChanged(nameof(LocationSummary));
        }
        private void SetPosition(GeoPoint p,string name)
        {
            settingPosition=true;
            Latitude=p.Latitude.ToString("0.000000",Invariant); Longitude=p.Longitude.ToString("0.000000",Invariant);
            settingPosition=false; LocationName=string.IsNullOrWhiteSpace(name)?"Picked point":name;
            Calculate();
        }
        private void SetFirstDate(long day)
        { int[] c=CivilDate.toCivil(day); firstDate=new DateTime(c[0],c[1],c[2]); NotifyPropertyChanged(nameof(FirstDate)); }
        private void UseTonight()
        {
            if(!double.TryParse(Longitude,NumberStyles.Float,Invariant,out double lon) || !Finite(lon) || Math.Abs(lon)>180) { Calculate(); return; }
            long day=NightCalculator.nightOf(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),lon);
            manualDate=false; selectionOverride=day; SetFirstDate(day); Calculate();
        }
        private void ShiftNight(int delta)
        {
            if(SelectedReport==null) return;
            long day=SelectedReport.epochDay+delta; int year=CivilDate.toCivil(day)[0]; if(year<1900 || year>2100) return;
            manualDate=true;
            var row=Rows.FirstOrDefault(r=>r.Report.epochDay==day);
            if(row!=null) { SelectedRow=row; return; }
            selectionOverride=day; SetFirstDate(day); Calculate();
        }
        private void TogglePick()
        {
            if(picking) { CancelPickCommand.Execute(null); return; }
            try
            {
                map.PushMapEvents(); picking=true; map.MapClick+=MapPicked; map.ItemClick+=MapPicked; map.MapLongPress+=MapPicked;
                InputManager.Current.PreProcessInput+=PickKey;
                NotifyPropertyChanged(nameof(PickLabel)); NotifyPropertyChanged(nameof(IsPicking)); Status="Select a point or marker on the map. Press Esc or Cancel pick to finish.";
            }
            catch(Exception e) { EndPick(); Status="Could not begin map selection: "+e.Message; }
        }
        private void MapPicked(object sender,MapMouseEventArgs e)
        {
            if(!picking) return;
            // A symbol can be clicked off its anchor; use the marker's actual position.
            var p=(e.Item as WinTak.Graphics.MapMarker)?.Position??e.WorldLocation;
            if(!ValidPoint(p)) return;
            string name=(e.Item as WinTak.Graphics.MapMarker)?.Name;
            EndPick(); SetPosition(p,name);
        }
        private void PickKey(object sender,PreProcessInputEventArgs e)
        {
            // Only intercept Escape while Illum owns map picking, including when the map has focus.
            if(picking && e.StagingItem.Input is KeyEventArgs key && (key.RoutedEvent==Keyboard.PreviewKeyDownEvent || key.RoutedEvent==Keyboard.KeyDownEvent) && key.Key==Key.Escape && !key.Handled)
            { CancelPickCommand.Execute(null); key.Handled=true; }
        }
        private void EndPick()
        {
            if(!picking) return;
            map.MapClick-=MapPicked; map.ItemClick-=MapPicked; map.MapLongPress-=MapPicked; picking=false;
            InputManager.Current.PreProcessInput-=PickKey;
            try { map.PopMapEvents(); } catch(Exception e) { Trace.TraceWarning("Illum map event restore: "+e); }
            NotifyPropertyChanged(nameof(PickLabel)); NotifyPropertyChanged(nameof(IsPicking));
        }
        private async void Calculate()
        {
            if(!Dispatcher.CheckAccess()) { _ = Dispatcher.BeginInvoke((Action)Calculate); return; }
            if(disposed) return;
            Inputs input;
            ClearInputErrors();
            try { input=ReadInputs(); }
            catch(InputException e) { RejectInput(e.Field,e.Message); return; }
            EndPick(); CancelCalculation();
            selectionOverride=null;
            var source=new CancellationTokenSource(); cancellation=source; var token=source.Token; int request=generation;
            Status=Terrain?"Reading terrain and calculating nights…":"Calculating nights…";
            // Clear the previous plan so old results cannot look like the new location/settings.
            ClearReport("Calculating…");
            SaveSettings();
            try
            {
                var result=await Task.Run(async ()=>
                {
                    await worker.WaitAsync(token).ConfigureAwait(false);
                    try
                    {
                        token.ThrowIfCancellationRequested();
                        string warning="";
                        if(input.Zone==0 && tzMap==null && !timezoneMapFailed)
                            try { tzMap=loadTimezoneMap(); } catch(Exception e) when(e is IOException || e is InvalidDataException)
                            { timezoneMapFailed=true; Trace.TraceWarning("Illum timezone grid: "+e.Message); }
                        if(input.Zone==0 && tzMap==null) warning="Timezone map unavailable: using military / nautical time without DST.";
                        DisplayZone zone=input.Zone==0?Core.DisplayZone.atPoint(tzMap,input.Lat,input.Lon)
                            :input.Zone==1?Core.DisplayZone.ZULU:input.Zone==2?Core.DisplayZone.device()
                            :input.Zone==3?Core.DisplayZone.nautical(input.Lon):Core.DisplayZone.fixedMinutes(input.Offset);
                        HorizonProfile profile=null;
                        if(input.Terrain)
                        {
                            var sampler=new ElevationSampler(elevation,token);
                            profile=HorizonProfile.build(input.Lat,input.Lon,sampler,HorizonProfile.DEFAULT_RADIUS_M);
                            if(sampler.Failures>0) warning+=(warning.Length>0?Environment.NewLine:"")+"Some elevation queries failed; terrain coverage may be incomplete.";
                        }
                        var reports=new List<NightReport>(input.Nights);
                        for(int i=0;i<input.Nights;i++) { token.ThrowIfCancellationRequested(); reports.Add(NightCalculator.compute(input.Lat,input.Lon,input.Day+i,zone,input.Settings,profile)); }
                        return new Result { Zone=zone, Profile=profile, Reports=reports, Warning=warning };
                    }
                    finally { worker.Release(); }
                },token);
                if(disposed || request!=generation) return;
                DisplayZone=result.Zone; resultSettings=input.Settings; resultZulu=input.Zulu;
                resultHorizon=result.Profile; resultWarning=result.Warning; resultTerrainRequested=input.Terrain; lastPlanFirstDay=input.Day;
                NotifyPropertyChanged(nameof(SelectedSettings));
                foreach(var report in result.Reports) Rows.Add(new NightRow(report,result.Zone));
                SelectedRow=Rows.FirstOrDefault(r=>r.Report.epochDay==input.SelectedDay)??Rows.FirstOrDefault();
                StandardReference=input.Settings.standard.reference+" Sky: "+input.Settings.sky.label+"."+(input.Settings.includeNightSky?" Includes 0.5 mlux night sky (before the sky factor).":"");
                Status=input.Nights+" nights at "+input.Lat.ToString("0.0000",Invariant)+", "+input.Lon.ToString("0.0000",Invariant)
                    +(input.Terrain?(result.Profile==null?". No terrain at this point; showing flat-terrain doctrine.":". Terrain coverage: "+Formats.percent(result.Profile.coverage)+" within 20 km."):". Flat-terrain doctrine.");
            }
            catch(OperationCanceledException) { }
            catch(Exception e)
            {
                Trace.TraceError("Illum calculation: "+e);
                if(!disposed && request==generation) { ClearReport("No result"); Status="Calculation failed: "+e.Message; }
            }
            finally { if(ReferenceEquals(cancellation,source)) cancellation=null; source.Dispose(); }
        }
        private Inputs ReadInputs()
        {
            double lat=Number(Latitude,-90,90,"Latitude must be between −90 and 90 decimal degrees.",nameof(Latitude));
            double lon=Number(Longitude,-180,180,"Longitude must be between −180 and 180 decimal degrees.",nameof(Longitude));
            if(!manualDate) SetFirstDate(NightCalculator.nightOf(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),lon));
            if(!int.TryParse(Nights,NumberStyles.Integer,Invariant,out int nights) || nights<7 || nights>90) throw new InputException(nameof(Nights),"Choose 7 to 90 nights.");
            if(!FirstDate.HasValue || FirstDate.Value.Year<1900 || FirstDate.Value.Year>2100) throw new InputException(nameof(FirstDate),"Choose an evening date from 1900 to 2100.");
            CheckIndex(StandardIndex,Standards,nameof(StandardIndex)); CheckIndex(MissionIndex,Missions,nameof(MissionIndex)); CheckIndex(SkyIndex,Skies,nameof(SkyIndex));
            CheckIndex(WindowIndex,Windows,nameof(WindowIndex)); CheckIndex(ZoneIndex,Zones,nameof(ZoneIndex)); CheckIndex(CustomMethodIndex,Methods,nameof(CustomMethodIndex));
            LightStandard standard=StandardIndex==0?LightStandard.USAF:StandardIndex==1?LightStandard.USN:LightStandard.ARMY;
            if(StandardIndex==3) standard=LightStandard.custom((LightStandard.Method)CustomMethodIndex,
                Number(CustomMillilux,.01,1000,"Custom illuminance must be 0.01–1000 millilux.",nameof(CustomMillilux))/1000,
                Number(CustomPercent,0,100,"Moon illumination must be 0–100%.",nameof(CustomPercent)),Number(CustomAltitude,0,90,"Moon elevation must be 0–90°.",nameof(CustomAltitude)));
            double tolerance=Number(TolerancePercent,0,49,"Mixed tolerance must be 0–49%.",nameof(TolerancePercent))/100;
            int start=1320,end=240;
            if(WindowIndex==2) { start=Clock(CustomStart,nameof(CustomStart)); end=Clock(CustomEnd,nameof(CustomEnd)); }
            int offset=0;
            if(ZoneIndex==4 && (!int.TryParse(ManualOffset,NumberStyles.Integer,Invariant,out offset) || offset< -840 || offset>840)) throw new InputException(nameof(ManualOffset),"Manual offset must be −840 to +840 minutes from UTC.");
            var date=FirstDate.Value;
            long day=CivilDate.fromCivil(date.Year,date.Month,date.Day);
            return new Inputs { Lat=lat,Lon=lon,Day=day,SelectedDay=selectionOverride??(lastPlanFirstDay==day?SelectedReport?.epochDay:day)??day,Nights=nights,Zone=ZoneIndex,Offset=offset,Terrain=Terrain,Zulu=ShowZulu,
                Settings=new PlanSettings.Builder().standard(standard).mission((PlanSettings.Mission)MissionIndex).sky(PlanSettings.Sky.All[SkyIndex])
                    .window((PlanSettings.WindowMode)WindowIndex).customWindow(start,end).tolerance(tolerance).includeNightSky(IncludeNightSky).build() };
        }
        private static void CheckIndex(int index,string[] choices,string field)
        { if(index<0 || index>=choices.Length) throw new InputException(field,"Choose an option from the list."); }
        private static double Number(string text,double min,double max,string error,string field)
        {
            if(!double.TryParse(text,NumberStyles.Float,Invariant,out double value) || double.IsNaN(value) || double.IsInfinity(value) || value<min || value>max) throw new InputException(field,error);
            return value;
        }
        private static int Clock(string text,string field)
        {
            string value=text?.Trim();
            if(value!=null && value.EndsWith("L",StringComparison.OrdinalIgnoreCase)) value=value.Substring(0,value.Length-1);
            if(!DateTime.TryParseExact(value,new[]{"HH:mm","HHmm","Hmm"},Invariant,DateTimeStyles.None,out DateTime time)) throw new InputException(field,"Use HH:mm or HHmm (24-hour clock).");
            return time.Hour*60+time.Minute;
        }
        private static bool Finite(double value)=>!double.IsNaN(value) && !double.IsInfinity(value);
        internal void RejectInput(string field,string message)
        {
            EndPick(); CancelCalculation(); ClearReport("No result"); Status=message;
            ClearInputErrors(); inputErrors[field]=message; ErrorsChanged?.Invoke(this,new DataErrorsChangedEventArgs(field));
            errorField=field; NotifyPropertyChanged(nameof(ErrorField)); NotifyPropertyChanged(nameof(HasErrors));
        }
        private void ClearInputErrors()
        {
            var fields=inputErrors.Keys.ToArray(); inputErrors.Clear(); errorField="";
            foreach(string field in fields) ErrorsChanged?.Invoke(this,new DataErrorsChangedEventArgs(field));
            NotifyPropertyChanged(nameof(HasErrors));
        }
        private sealed class InputException : ArgumentException
        { internal string Field { get; } internal InputException(string field,string message):base(message) { Field=field; } }
        private void ClearReport(string title)
        {
            SelectedRow=null; SelectedReport=null; Rows.Clear(); KeyTimes.Clear(); Headline=title; Detail=""; ShadowText=""; Doctrine=""; Notes="";
            ZoneDescription=""; StandardReference=""; RatingBrush=Brushes.DimGray; resultHorizon=null; resultWarning="";
        }
        private void Render(NightReport n)
        {
            bool changingNight=selectedReport!=null && !ReferenceEquals(selectedReport,n);
            if(changingNight) SelectedNightChanging?.Invoke(this,EventArgs.Empty);
            SelectedReport=n;
            string mission=resultSettings?.mission==PlanSettings.Mission.CONCEAL?"ground concealment":"NVG / aviation";
            Headline=CivilDate.label(n.epochDay)+" · "+(n.level==NightReport.LightLevel.NONE?"No rated window":n.level.ToString())+" · "+mission;
            RatingBrush=n.rating==NightReport.Rating.GREEN?new SolidColorBrush(Color.FromRgb(32,113,67)):
                n.rating==NightReport.Rating.AMBER?new SolidColorBrush(Color.FromRgb(139,94,12)):
                n.rating==NightReport.Rating.RED?new SolidColorBrush(Color.FromRgb(155,48,48)):Brushes.DimGray;
            string T(long time)=>Formats.timeWithZulu(time,DisplayZone,resultZulu);
            KeyTimes.Clear();
            KeyTimes.Add(new TimeCard("Sunset",T(n.sunset))); KeyTimes.Add(new TimeCard("EENT",T(n.eent)));
            KeyTimes.Add(new TimeCard("BMNT",T(n.bmnt))); KeyTimes.Add(new TimeCard("Sunrise",T(n.sunrise)));
            string MoonTimes(bool rise)
            {
                var events=n.moonEvents.Where(e=>e.rise==rise).Select(e=>T(e.time)+" @ "+Formats.degrees(e.azimuthDeg)).ToArray();
                return events.Length>0?string.Join(Environment.NewLine,events):n.moonUpAllSpan?"Up all night":n.moonDownAllSpan?"Down all night":"No event this night";
            }
            KeyTimes.Add(new TimeCard("Moonrise",MoonTimes(true))); KeyTimes.Add(new TimeCard("Moonset",MoonTimes(false)));
            if(n.terrainApplied)
                foreach(bool rise in new[] {true,false})
                {
                    var events=n.terrainMoonEvents.Where(e=>e.rise==rise && !n.moonEvents.Any(a=>a.rise==rise && Math.Abs(a.time-e.time)<120000))
                        .Select(e=>T(e.time)+" @ "+Formats.degrees(e.azimuthDeg)).ToArray();
                    if(events.Length>0) KeyTimes.Add(new TimeCard(rise?"Clears terrain":"Behind terrain",string.Join(Environment.NewLine,events)));
                }
            KeyTimes.Add(new TimeCard("Moon maximum",!n.hasWindow()?"--":n.moonMaxAltDeg<0?"Down throughout window":Formats.altitude(n.moonMaxAltDeg)+" @ "+T(n.moonMaxAltTime)));
            KeyTimes.Add(new TimeCard("Peak moonlight",!n.hasWindow()?"--":n.peakMoonLux<=0?"None":Formats.lux(n.peakMoonLux)+" @ "+T(n.peakMoonLuxTime)));
            var text=new StringBuilder();
            text.AppendLine("Sunset   "+T(n.sunset)); text.AppendLine("Civil end "+T(n.eect)); text.AppendLine("EENT     "+T(n.eent)); text.AppendLine("Astro end "+T(n.eeat));
            text.AppendLine("Astro begin "+T(n.bmat)); text.AppendLine("BMNT     "+T(n.bmnt)); text.AppendLine("Civil begin "+T(n.bmct)); text.AppendLine("Sunrise  "+T(n.sunrise));
            if(n.sunUpAllSpan) text.AppendLine("Sun above horizon throughout this night frame.");
            if(n.sunDownAllSpan) text.AppendLine("Sun below horizon throughout this night frame.");
            text.AppendLine("Moon: "+n.illumPercent.ToString("0",Invariant)+"% · "+n.phaseName);
            foreach(var e in n.moonEvents) text.AppendLine((e.rise?"Moonrise ":"Moonset  ")+T(e.time)+" @ "+Formats.degrees(e.azimuthDeg));
            if(n.moonUpAllSpan) text.AppendLine("Moon up throughout the night frame.");
            if(n.moonDownAllSpan) text.AppendLine("Moon down throughout the night frame.");
            foreach(var e in n.terrainMoonEvents) text.AppendLine((e.rise?"Clears terrain ":"Behind terrain ")+T(e.time)+" @ "+Formats.degrees(e.azimuthDeg));
            text.AppendLine("Window: "+n.windowLabel+" · "+T(n.windowStart)+"–"+T(n.windowEnd));
            text.AppendLine("HLL: "+Formats.percent(n.hllFraction)+" · peak moonlight "+Formats.lux(n.peakMoonLux)+" at "+T(n.peakMoonLuxTime));
            text.AppendLine("Moon highest: "+Formats.altitude(n.moonMaxAltDeg)+" at "+T(n.moonMaxAltTime));
            foreach(var p in n.hllPeriods) text.AppendLine("HLL "+T(p.start)+"–"+T(p.end)+(p.twilightOnly?" (twilight)":""));
            string clock=Formats.clockChange(n,DisplayZone); if(clock!=null) text.AppendLine(clock);
            Detail=text.ToString().TrimEnd();
            Doctrine=n.terrainApplied?"Flat-terrain doctrine: "+n.doctrineRating+" · "+n.doctrineLevel+" · HLL "+Formats.percent(n.doctrineHllFraction):"";
            ShadowText=n.hasWindow()?n.shadows.headline()+Environment.NewLine+string.Join(Environment.NewLine,n.shadows.lines(DisplayZone)):"No rated window for a shadow outlook.";
            ZoneDescription=DisplayZone.describe(n.refTime);
            var explanation=new List<string>();
            if(!string.IsNullOrEmpty(resultWarning)) explanation.Add(resultWarning);
            if(clock!=null) explanation.Add(clock);
            if(resultTerrainRequested && resultHorizon==null) explanation.Add("No terrain data at this point: showing flat-terrain doctrine.");
            if(resultHorizon!=null)
            {
                double[] highest=resultHorizon.highest();
                explanation.Add("Terrain skyline within 20 km: highest "+Formats.altitude(highest[0])+" toward "+ShadowOutlook.compass8(highest[1])+". Bare ground at this exact point.");
                if(resultHorizon.coverage<.9) explanation.Add("Terrain coverage is "+Formats.percent(resultHorizon.coverage)+"; missing directions are treated as flat.");
            }
            if(n.noNauticalDarkness) explanation.Add("The sun never reaches 12° below the horizon this night: no EENT/BMNT.");
            else if(n.allNauticalDark) explanation.Add("Polar night: nautical darkness throughout the entire night frame.");
            if(n.level!=NightReport.LightLevel.HLL && n.hllPeriods.Count>0) explanation.Add("HLL: "+string.Join(", ",n.hllPeriods.Select(p=>Formats.time(p.start,DisplayZone)+"–"+Formats.time(p.end,DisplayZone)+(p.twilightOnly?" (twilight)":"")))+". The rest of the rated window is LLL.");
            if(n.lowMoonFraction>=.1 && n.level!=NightReport.LightLevel.LLL) explanation.Add("Moon below "+Formats.altitude(SelectedSettings.standard.minMoonAltDeg)+" for "+Formats.percent(n.lowMoonFraction)+" of the window; low moon angles can cause NVG glare.");
            if(SelectedSettings.standard.method==LightStandard.Method.MOON_RULE) explanation.Add("Rating uses moon illumination and elevation (≥"+SelectedSettings.standard.minIllumPercent.ToString("0",Invariant)+"%, ≥"+Formats.altitude(SelectedSettings.standard.minMoonAltDeg)+"). The lux curve is a reference, not the rating rule.");
            if(SelectedSettings.windowMode==PlanSettings.WindowMode.CUSTOM && clock!=null) explanation.Add("Custom window endpoints follow local clock time across DST. Repeated times use the earlier occurrence; skipped times move forward by the clock-change gap.");
            Notes=string.Join(Environment.NewLine,explanation);
            if(changingNight) SelectedNightChanged?.Invoke(this,EventArgs.Empty);
        }
        private void LoadSettings()
        {
            loadingSettings=true;
            try { ReadSettings(); } finally { loadingSettings=false; }
        }
        private void ReadSettings()
        {
            bool failed=false;
            foreach(string path in new[] {settingsPath,settingsPath+".bak"})
            {
                if(!File.Exists(path)) continue;
                try
                {
                    SavedOptions saved;
                    using(var s=File.OpenRead(path)) saved=(SavedOptions)new DataContractJsonSerializer(typeof(SavedOptions)).ReadObject(s);
                    if(saved?.Values==null) throw new SerializationException("Preferences contain no options.");
                    foreach(var p in GetType().GetProperties().Where(p=>p.DeclaringType==typeof(IllumDockPane) && p.CanWrite && p.SetMethod?.IsPublic==true && p.Name!=nameof(FirstDate)))
                        if(saved.Values.TryGetValue(p.Name,out string value))
                        {
                            if(p.PropertyType==typeof(string)) p.SetValue(this,value);
                            else if(p.PropertyType==typeof(bool) && bool.TryParse(value,out bool b)) p.SetValue(this,b);
                            else if(p.PropertyType==typeof(int) && int.TryParse(value,NumberStyles.Integer,Invariant,out int i) && ValidSavedIndex(p.Name,i)) p.SetValue(this,i);
                        }
                    if(path!=settingsPath) Status="Recovered saved preferences from the backup.";
                    return;
                }
                catch(Exception e) { failed=true; Trace.TraceWarning("Illum preferences: "+e); }
            }
            if(failed) Status="Saved preferences could not be read; defaults are shown.";
        }
        private bool ValidSavedIndex(string name,int value)
        {
            int count=name==nameof(StandardIndex)?Standards.Length:name==nameof(MissionIndex)?Missions.Length:name==nameof(SkyIndex)?Skies.Length
                :name==nameof(WindowIndex)?Windows.Length:name==nameof(ZoneIndex)?Zones.Length:name==nameof(CustomMethodIndex)?Methods.Length:0;
            return value>=0 && value<count;
        }
        private void SaveSettings()
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(settingsPath));
                var values=GetType().GetProperties().Where(p=>p.DeclaringType==typeof(IllumDockPane) && p.CanRead && p.CanWrite && p.SetMethod?.IsPublic==true &&
                    (p.PropertyType==typeof(string)||p.PropertyType==typeof(bool)||p.PropertyType==typeof(int))).ToDictionary(p=>p.Name,p=>Convert.ToString(p.GetValue(this),Invariant));
                string pending=settingsPath+".tmp";
                using(var s=File.Create(pending)) new DataContractJsonSerializer(typeof(SavedOptions)).WriteObject(s,new SavedOptions { Values=values });
                if(File.Exists(settingsPath)) File.Replace(pending,settingsPath,settingsPath+".bak"); else File.Move(pending,settingsPath);
            }
            catch(Exception e) { Trace.TraceWarning("Illum preferences not saved: "+e); }
        }
        [DataContract] private sealed class SavedOptions { [DataMember] public Dictionary<string,string> Values; }
        private sealed class Inputs { internal double Lat,Lon; internal long Day,SelectedDay; internal int Nights,Zone,Offset; internal bool Terrain,Zulu; internal PlanSettings Settings; }
        private sealed class Result { internal DisplayZone Zone; internal HorizonProfile Profile; internal List<NightReport> Reports; internal string Warning; }
        private sealed class ElevationSampler : HorizonProfile.ElevationSampler
        {
            private readonly IElevationManager manager; private readonly CancellationToken token;
            internal int Failures { get; private set; }
            internal ElevationSampler(IElevationManager manager,CancellationToken token) { this.manager=manager; this.token=token; }
            public void sample(double[] lat,double[] lon,double[] output)
            {
                // MODEL_TERRAIN = 1: exclude DSM/building surface elevation from the skyline.
                var query=new TAKEngine.Elevation.ElevationManager.QueryParameters { elevationModel=1 };
                for(int i=0;i<lat.Length;i++)
                {
                    token.ThrowIfCancellationRequested();
                    try { double h=manager.GetElevation(lat[i],lon[i],query); if(!double.IsInfinity(h)) output[i]=h; }
                    catch(Exception e) { Failures++; if(Failures==1) Trace.TraceWarning("Illum elevation sample: "+e.Message); }
                }
            }
        }
        private sealed class Command : ICommand
        {
            private readonly Action run; internal Command(Action run) { this.run=run; }
            public bool CanExecute(object parameter)=>true;
            public void Execute(object parameter)=>run();
            public event EventHandler CanExecuteChanged { add { } remove { } }
        }
    }
    public sealed class NightRow
    {
        public NightReport Report { get; }
        public Brush RatingBrush { get; }
        public string Date { get; }
        public string Level { get; }
        public string Hll { get; }
        public string Eent { get; }
        public string Bmnt { get; }
        public string Moon { get; }
        public string Illumination { get; }
        public long EpochDay => Report.epochDay;
        public double HllFraction => Report.hllFraction;
        public double IllumPercent => Report.illumPercent;
        public int LightSort => (int)Report.rating;
        public string RiseSet { get; }
        internal NightRow(NightReport n,DisplayZone zone)
        { Report=n; Date=CivilDate.weekday(n.epochDay)+" "+CivilDate.shortLabel(n.epochDay); Level=n.level==NightReport.LightLevel.NONE?"N/A":n.level.ToString(); Hll=Formats.percent(n.hllFraction); Eent=Formats.time(n.eent,zone); Bmnt=Formats.time(n.bmnt,zone); Moon=n.illumPercent.ToString("0",CultureInfo.InvariantCulture)+"% "+n.phaseName;
            Illumination=n.illumPercent.ToString("0",CultureInfo.InvariantCulture)+"%";
            RiseSet=n.moonEvents.Count==0?(n.moonUpAllSpan?"Up all night":n.moonDownAllSpan?"Down all night":"No event"):string.Join(" / ",n.moonEvents.Select(e=>(e.rise?"R ":"S ")+Formats.time(e.time,zone)));
            RatingBrush=new SolidColorBrush(n.rating==NightReport.Rating.GREEN?Color.FromRgb(32,113,67):n.rating==NightReport.Rating.AMBER?Color.FromRgb(139,94,12):n.rating==NightReport.Rating.RED?Color.FromRgb(155,48,48):Color.FromRgb(70,80,90)); }
    }
    public sealed class TimeCard
    {
        public string Name { get; }
        public string Value { get; }
        internal TimeCard(string name,string value) { Name=name; Value=value; }
    }
}
