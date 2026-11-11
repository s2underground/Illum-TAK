using System.ComponentModel.Composition;
using WinTak.Framework.Docking;
using WinTak.Framework.Tools;
using WinTak.Framework.Tools.Attributes;

namespace Illum.WinTAK
{
    [Button("S2U_Illum_Button", "Illum", LargeImage = "pack://application:,,,/S2U.Illum.WinTAK;component/Assets/Illum.png",
        SmallImage = "pack://application:,,,/S2U.Illum.WinTAK;component/Assets/Illum.png")]
    public sealed class IllumButton : Button
    {
        private readonly IDockingManager docking;
        [ImportingConstructor]
        public IllumButton(IDockingManager docking) { this.docking = docking; }
        protected override void OnClick() { base.OnClick(); docking.GetDockPane(IllumDockPane.Id)?.Activate(); }
    }
}
