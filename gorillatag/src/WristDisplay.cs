using System.Text;
using TMPro;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>Hotbar and link status on the back of the left wrist.</summary>
    public sealed class WristDisplay
    {
        private readonly TextMeshPro text;
        private Messages.Hotbar hotbar;
        private string status = "";
        private bool connected;

        public WristDisplay(Transform leftController)
        {
            var go = new GameObject("GorillaCraft Wrist");
            go.transform.SetParent(leftController, false);
            go.transform.localPosition = new Vector3(0.0f, 0.06f, -0.08f);
            go.transform.localRotation = Quaternion.Euler(60f, 0f, 0f);
            text = go.AddComponent<TextMeshPro>();
            TMP_FontAsset font = TMP_Settings.defaultFontAsset;
            if (font == null)
            {
                var fonts = Resources.FindObjectsOfTypeAll<TMP_FontAsset>();
                if (fonts.Length > 0)
                    font = fonts[0];
            }
            if (font != null)
                text.font = font;
            text.fontSize = 0.22f;
            text.alignment = TextAlignmentOptions.TopLeft;
            text.textWrappingMode = TextWrappingModes.Normal;
            text.rectTransform.sizeDelta = new Vector2(0.22f, 0.3f);
            text.color = Color.white;
            go.SetActive(false);
        }

        public bool Visible
        {
            set => text.gameObject.SetActive(value);
        }

        public void SetHotbar(Messages.Hotbar h)
        {
            hotbar = h;
            Refresh();
        }

        public void SetStatus(string s)
        {
            status = s ?? "";
            Refresh();
        }

        public void SetConnected(bool c)
        {
            connected = c;
            Refresh();
        }

        private void Refresh()
        {
            var sb = new StringBuilder();
            sb.Append("<b>GorillaCraft</b> ").Append(connected ? "<color=#7f7>●</color>" : "<color=#f77>● no Minecraft</color>").Append('\n');
            if (hotbar != null)
            {
                for (int i = 0; i < 9; i++)
                {
                    string name = string.IsNullOrEmpty(hotbar.Names[i]) ? "<color=#888>-</color>" : hotbar.Names[i];
                    string count = hotbar.Counts[i] > 1 ? " x" + hotbar.Counts[i] : "";
                    if (i == hotbar.Selected)
                        sb.Append("<color=#ff0>> ").Append(i + 1).Append(' ').Append(name).Append(count).Append("</color>\n");
                    else
                        sb.Append("  ").Append(i + 1).Append(' ').Append(name).Append(count).Append('\n');
                }
            }
            if (status.Length > 0)
                sb.Append("<size=70%><color=#ccc>").Append(status).Append("</color></size>");
            text.text = sb.ToString();
        }
    }
}
