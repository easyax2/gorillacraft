using System;
using System.Collections.Generic;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// GorillaCraft's main menu, drawn over the Gorilla Tag window on your monitor. Mouse: hover
    /// and click. Keyboard: Up/Down to move, Enter to choose, Left/Right to change a setting,
    /// Esc to go back, F1 to open or close. Text fields take typing as usual.
    /// </summary>
    public sealed class MainMenu
    {
        /// <summary>What the menu needs from the plugin.</summary>
        public interface IHost
        {
            bool Connected { get; }
            bool HasWorld { get; }
            string WorldLabel { get; }
            bool InMap { get; }
            bool ModsAllowed { get; }
            string LastStatus { get; }
            void ToggleMap();
            void RequestWorlds();
            void OpenWorld(string id);
            void JoinServer(string address);
            void LeaveWorld();
        }

        public sealed class Setting
        {
            public string Name;
            public Func<string> Value;
            /// <summary>Called with -1 / +1 (Left/Right); toggles use either.</summary>
            public Action<int> Change;
        }

        private sealed class Item
        {
            public Func<string> Label;
            public Action Activate;
            public Action<int> Change;
            public bool TextField;
            public bool Enabled = true;
        }

        private enum Page { Main, Singleplayer, Multiplayer, Settings }

        private readonly IHost host;
        private readonly List<Setting> settings;
        private readonly Func<List<string>> getServers;
        private readonly Action<List<string>> saveServers;

        private bool open;
        private Page page = Page.Main;
        private int selected;
        private readonly List<Item> items = new List<Item>();
        private Messages.Worlds worlds;
        private string address = "";
        private string newWorld = "";
        private Vector2 scroll;
        private GUIStyle box, title, item, itemSelected, itemDisabled, small, field;
        private float scale = 1f;
        private Vector2 lastMouse;
        private bool mouseMoved;

        public MainMenu(IHost host, List<Setting> settings, Func<List<string>> getServers, Action<List<string>> saveServers, bool openAtStart)
        {
            this.host = host;
            this.settings = settings;
            this.getServers = getServers;
            this.saveServers = saveServers;
            open = openAtStart;
        }

        public bool IsOpen => open;

        public void SetWorlds(Messages.Worlds w) => worlds = w;

        public void Toggle()
        {
            open = !open;
            if (open)
                Go(Page.Main);
        }

        private void Go(Page p)
        {
            page = p;
            selected = 0;
            scroll = Vector2.zero;
            if (p == Page.Singleplayer)
            {
                worlds = null;
                host.RequestWorlds();
            }
            if (p == Page.Multiplayer)
            {
                var servers = getServers();
                if (address.Length == 0 && servers.Count > 0)
                    address = servers[0];
            }
        }

        private void Build()
        {
            items.Clear();
            switch (page)
            {
                case Page.Main:
                    items.Add(new Item
                    {
                        Label = () => host.InMap ? "Leave the Minecraft map" : "Enter the Minecraft map",
                        Activate = () => host.ToggleMap(),
                        Enabled = host.InMap || (host.Connected && host.HasWorld && host.ModsAllowed),
                    });
                    items.Add(new Item { Label = () => "Singleplayer worlds", Activate = () => Go(Page.Singleplayer), Enabled = host.Connected });
                    items.Add(new Item { Label = () => "Multiplayer (join a server)", Activate = () => Go(Page.Multiplayer), Enabled = host.Connected });
                    items.Add(new Item { Label = () => "Leave the current world", Activate = () => host.LeaveWorld(), Enabled = host.Connected && host.HasWorld });
                    items.Add(new Item { Label = () => "Settings", Activate = () => Go(Page.Settings) });
                    items.Add(new Item { Label = () => "Close menu (F1 opens it again)", Activate = () => open = false });
                    break;

                case Page.Singleplayer:
                    items.Add(new Item { Label = () => "New world:", TextField = true, Activate = CreateWorld });
                    items.Add(new Item { Label = () => "Create and play", Activate = CreateWorld, Enabled = newWorld.Trim().Length > 0 });
                    if (worlds != null)
                    {
                        for (int i = 0; i < worlds.Ids.Length; i++)
                        {
                            string id = worlds.Ids[i], name = worlds.Names[i];
                            items.Add(new Item { Label = () => name == id ? name : $"{name}  <color=#999>({id})</color>", Activate = () => { host.OpenWorld(id); open = false; } });
                        }
                    }
                    items.Add(new Item { Label = () => "Back", Activate = () => Go(Page.Main) });
                    break;

                case Page.Multiplayer:
                    items.Add(new Item { Label = () => "Server address:", TextField = true, Activate = () => Join(address) });
                    items.Add(new Item { Label = () => "Join", Activate = () => Join(address), Enabled = address.Trim().Length > 0 });
                    foreach (string s in getServers())
                    {
                        string a = s;
                        items.Add(new Item { Label = () => a + "  <color=#999>(Delete: forget)</color>", Activate = () => Join(a), Change = d => { } });
                    }
                    items.Add(new Item { Label = () => "Back", Activate = () => Go(Page.Main) });
                    break;

                case Page.Settings:
                    foreach (Setting s in settings)
                    {
                        Setting st = s;
                        items.Add(new Item { Label = () => $"{st.Name}:  <b>{st.Value()}</b>", Activate = () => st.Change(1), Change = st.Change });
                    }
                    items.Add(new Item { Label = () => "Back", Activate = () => Go(Page.Main) });
                    break;
            }
            selected = Mathf.Clamp(selected, 0, items.Count - 1);
        }

        private void CreateWorld()
        {
            string name = newWorld.Trim();
            if (name.Length == 0)
                return;
            host.OpenWorld(name);
            newWorld = "";
            open = false;
        }

        private void Join(string a)
        {
            a = (a ?? "").Trim();
            if (a.Length == 0)
                return;
            var servers = getServers();
            servers.RemoveAll(s => string.Equals(s, a, StringComparison.OrdinalIgnoreCase));
            servers.Insert(0, a);
            if (servers.Count > 10)
                servers.RemoveRange(10, servers.Count - 10);
            saveServers(servers);
            host.JoinServer(a);
            open = false;
        }

        /// <summary>Call from the plugin's OnGUI.</summary>
        public void OnGUI()
        {
            Event e = Event.current;
            if (e.type == EventType.KeyDown && e.keyCode == KeyCode.F1)
            {
                Toggle();
                e.Use();
            }
            if (!open)
                return;

            if (e.type == EventType.Repaint)
            {
                mouseMoved = e.mousePosition != lastMouse;
                lastMouse = e.mousePosition;
            }
            Cursor.visible = true;
            Cursor.lockState = CursorLockMode.None;
            Styles();
            Build();
            HandleKeys(e);
            if (!open)
                return;

            float w = 560 * scale, h = Mathf.Min(Screen.height - 40, 640 * scale);
            var area = new Rect((Screen.width - w) / 2, (Screen.height - h) / 2, w, h);
            GUI.Box(area, GUIContent.none, box);
            GUILayout.BeginArea(new Rect(area.x + 20 * scale, area.y + 16 * scale, area.width - 40 * scale, area.height - 32 * scale));
            GUILayout.Label(page == Page.Main ? "GorillaCraft" : "GorillaCraft  ›  " + page, title);
            GUILayout.Label(StatusText(), small);
            GUILayout.Space(8 * scale);

            scroll = GUILayout.BeginScrollView(scroll);
            for (int i = 0; i < items.Count; i++)
                DrawItem(i, items[i]);
            GUILayout.EndScrollView();

            GUILayout.Label("Mouse or ↑ ↓ Enter · ← → change · Esc back · F1 close", small);
            GUILayout.EndArea();
        }

        private string StatusText()
        {
            string mc = host.Connected ? "<color=#7f7>Minecraft connected</color>" : "<color=#f77>Minecraft not connected</color>";
            string world = host.HasWorld ? $"in <b>{host.WorldLabel}</b>" : "no world open";
            string map = host.InMap ? "you're in the map" : host.ModsAllowed ? "you're in Gorilla Tag" : "<color=#fb7>public lobby: GorillaCraft is off</color>";
            string last = string.IsNullOrEmpty(host.LastStatus) ? "" : "\n<color=#ccc>" + host.LastStatus + "</color>";
            return $"{mc} · {world} · {map}{last}";
        }

        private void DrawItem(int i, Item it)
        {
            GUIStyle style = !it.Enabled ? itemDisabled : i == selected ? itemSelected : item;
            if (it.TextField)
            {
                GUILayout.BeginHorizontal();
                GUILayout.Label(it.Label(), i == selected ? itemSelected : item, GUILayout.Width(170 * scale));
                string name = "gc-field-" + page;
                GUI.SetNextControlName(name);
                if (page == Page.Multiplayer)
                    address = GUILayout.TextField(address, 100, field);
                else
                    newWorld = GUILayout.TextField(newWorld, 60, field);
                if (i == selected && GUI.GetNameOfFocusedControl() != name)
                    GUI.FocusControl(name);
                GUILayout.EndHorizontal();
                Rect r = GUILayoutUtility.GetLastRect();
                if (Event.current.type == EventType.MouseDown && r.Contains(Event.current.mousePosition))
                    selected = i;
                return;
            }
            if (GUILayout.Button(it.Label(), style))
            {
                selected = i;
                if (it.Enabled)
                    it.Activate?.Invoke();
            }
            // Hovering selects, so mouse and keyboard share one highlight.
            if (Event.current.type == EventType.Repaint && mouseMoved && GUILayoutUtility.GetLastRect().Contains(Event.current.mousePosition))
                selected = i;
        }

        private void HandleKeys(Event e)
        {
            if (e.type != EventType.KeyDown || items.Count == 0)
                return;
            Item cur = items[selected];
            switch (e.keyCode)
            {
                case KeyCode.UpArrow:
                    Move(-1);
                    break;
                case KeyCode.DownArrow:
                case KeyCode.Tab:
                    Move(1);
                    break;
                case KeyCode.Return:
                case KeyCode.KeypadEnter:
                    if (cur.Enabled)
                        cur.Activate?.Invoke();
                    break;
                case KeyCode.LeftArrow:
                    if (cur.TextField)
                        return; // move the text cursor
                    cur.Change?.Invoke(-1);
                    break;
                case KeyCode.RightArrow:
                    if (cur.TextField)
                        return;
                    cur.Change?.Invoke(1);
                    break;
                case KeyCode.Delete:
                    if (cur.TextField || page != Page.Multiplayer || cur.Change == null)
                        return;
                    var servers = getServers();
                    int index = selected - 2;
                    if (index >= 0 && index < servers.Count)
                    {
                        servers.RemoveAt(index);
                        saveServers(servers);
                    }
                    break;
                case KeyCode.Escape:
                    if (page == Page.Main)
                        open = false;
                    else
                        Go(Page.Main);
                    break;
                default:
                    return;
            }
            e.Use();
        }

        private void Move(int d)
        {
            for (int n = 0; n < items.Count; n++)
            {
                selected = (selected + d + items.Count) % items.Count;
                if (items[selected].Enabled)
                    break;
            }
            if (!items[selected].TextField)
                GUI.FocusControl(null);
        }

        private void Styles()
        {
            float s = Mathf.Clamp(Screen.height / 1080f, 0.75f, 2.5f);
            if (box != null && Mathf.Approximately(s, scale))
                return;
            scale = s;
            var dark = Tex(new Color(0.08f, 0.09f, 0.08f, 0.94f));
            var hi = Tex(new Color(0.33f, 0.55f, 0.22f, 1f));
            var mid = Tex(new Color(0.18f, 0.2f, 0.18f, 1f));
            box = new GUIStyle(GUI.skin.box) { normal = { background = dark } };
            title = new GUIStyle(GUI.skin.label) { fontSize = (int)(28 * s), fontStyle = FontStyle.Bold, richText = true, normal = { textColor = Color.white } };
            small = new GUIStyle(GUI.skin.label) { fontSize = (int)(14 * s), richText = true, wordWrap = true, normal = { textColor = new Color(0.85f, 0.85f, 0.85f) } };
            item = new GUIStyle(GUI.skin.button)
            {
                fontSize = (int)(19 * s), alignment = TextAnchor.MiddleLeft, richText = true,
                padding = new RectOffset((int)(14 * s), 8, (int)(8 * s), (int)(8 * s)),
                normal = { background = mid, textColor = Color.white }, hover = { background = mid, textColor = Color.white },
                active = { background = hi, textColor = Color.white },
            };
            itemSelected = new GUIStyle(item) { normal = { background = hi, textColor = Color.white }, hover = { background = hi, textColor = Color.white } };
            itemDisabled = new GUIStyle(item) { normal = { background = mid, textColor = new Color(1, 1, 1, 0.35f) }, hover = { background = mid, textColor = new Color(1, 1, 1, 0.35f) } };
            field = new GUIStyle(GUI.skin.textField) { fontSize = (int)(19 * s), padding = new RectOffset(8, 8, (int)(8 * s), (int)(8 * s)) };
        }

        private static Texture2D Tex(Color c)
        {
            var t = new Texture2D(1, 1);
            t.SetPixel(0, 0, c);
            t.Apply();
            return t;
        }
    }
}
