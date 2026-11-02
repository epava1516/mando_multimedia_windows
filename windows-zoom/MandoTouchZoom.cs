using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;

// One bounded native touch gesture; no network, shell commands, service or stored secrets.
internal static class MandoTouchZoom {
    private const uint PT_TOUCH = 2, INRANGE = 0x2, INCONTACT = 0x4;
    private const uint DOWN = 0x10000, UPDATE = 0x20000, UP = 0x40000, CANCELED = 0x8000;
    private const int ERROR_NOT_READY = 21, ERROR_CANCELLED = 1223;
    private const int CONTACT_HALF = 3, EDGE_GAP = 12;

    [StructLayout(LayoutKind.Sequential, Pack = 8)]
    private struct POINT { public int X, Y; public POINT(int x, int y) { X = x; Y = y; } }
    [StructLayout(LayoutKind.Sequential, Pack = 8)]
    private struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential, Pack = 8)]
    private struct POINTER_INFO {
        public uint pointerType, pointerId, frameId, pointerFlags;
        public IntPtr sourceDevice, hwndTarget;
        public POINT ptPixelLocation, ptHimetricLocation, ptPixelLocationRaw, ptHimetricLocationRaw;
        public uint dwTime, historyCount;
        public int InputData;
        public uint dwKeyStates;
        public ulong PerformanceCount;
        public uint ButtonChangeType;
    }
    [StructLayout(LayoutKind.Sequential, Pack = 8)]
    private struct POINTER_TOUCH_INFO {
        public POINTER_INFO pointerInfo;
        public uint touchFlags, touchMask;
        public RECT rcContact, rcContactRaw;
        public uint orientation, pressure;
    }
    [StructLayout(LayoutKind.Sequential, Pack = 8)]
    private struct MONITORINFO { public uint cbSize; public RECT rcMonitor, rcWork; public uint dwFlags; }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool IsProcessDPIAware();
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool InitializeTouchInjection(uint maxCount, uint mode);
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool InjectTouchInput(uint count,
        [In, MarshalAs(UnmanagedType.LPArray, SizeParamIndex = 0)] POINTER_TOUCH_INFO[] contacts);
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint processId);
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool GetClientRect(IntPtr hwnd, out RECT rect);
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool ClientToScreen(IntPtr hwnd, ref POINT point);
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool GetCursorPos(out POINT point);
    [DllImport("user32.dll")] private static extern IntPtr MonitorFromPoint(POINT point, uint flags);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] private static extern bool GetMonitorInfo(IntPtr monitor, ref MONITORINFO info);
    [DllImport("user32.dll")] private static extern IntPtr WindowFromPoint(POINT point);
    [DllImport("user32.dll")] private static extern IntPtr GetAncestor(IntPtr hwnd, uint flags);
    [DllImport("user32.dll")] private static extern short GetAsyncKeyState(int key);

    private static bool LayoutValid() {
        int pointers = IntPtr.Size, infoSize = pointers == 8 ? 96 : 88;
        return Marshal.SizeOf(typeof(POINT)) == 8 && Marshal.SizeOf(typeof(RECT)) == 16 &&
            Marshal.SizeOf(typeof(POINTER_INFO)) == infoSize &&
            Marshal.SizeOf(typeof(POINTER_TOUCH_INFO)) == infoSize + 48 &&
            Marshal.OffsetOf(typeof(POINTER_INFO), "sourceDevice").ToInt32() == 16 &&
            Marshal.OffsetOf(typeof(POINTER_INFO), "ptPixelLocation").ToInt32() == 16 + 2 * pointers &&
            Marshal.OffsetOf(typeof(POINTER_INFO), "PerformanceCount").ToInt32() == 64 + 2 * pointers &&
            Marshal.OffsetOf(typeof(POINTER_TOUCH_INFO), "rcContact").ToInt32() == infoSize + 8;
    }
    private static bool FirefoxForeground(out IntPtr window) {
        window = GetForegroundWindow();
        if (window == IntPtr.Zero) return false;
        uint id;
        if (GetWindowThreadProcessId(window, out id) == 0 || id == 0) return false;
        try {
            using (Process process = Process.GetProcessById((int)id)) {
                return String.Equals(process.ProcessName, "firefox", StringComparison.OrdinalIgnoreCase);
            }
        } catch { return false; }
    }
    private static bool ClientBounds(IntPtr window, out RECT rect) {
        rect = new RECT(); RECT client;
        if (!GetClientRect(window, out client)) return false;
        POINT a = new POINT(client.Left, client.Top), b = new POINT(client.Right, client.Bottom);
        if (!ClientToScreen(window, ref a) || !ClientToScreen(window, ref b)) return false;
        rect.Left = a.X; rect.Top = a.Y; rect.Right = b.X; rect.Bottom = b.Y;
        return rect.Right > rect.Left && rect.Bottom > rect.Top;
    }
    private static bool SameRect(RECT a, RECT b) {
        return a.Left == b.Left && a.Top == b.Top && a.Right == b.Right && a.Bottom == b.Bottom;
    }
    private static bool PointTargetsWindow(POINT point, IntPtr window) {
        IntPtr hit = WindowFromPoint(point);
        return hit != IntPtr.Zero && GetAncestor(hit, 2) == window;
    }
    private static POINTER_TOUCH_INFO Contact(uint id, int x, int y, uint flags) {
        POINTER_TOUCH_INFO value = new POINTER_TOUCH_INFO();
        value.pointerInfo.pointerType = PT_TOUCH; value.pointerInfo.pointerId = id;
        value.pointerInfo.pointerFlags = flags;
        value.pointerInfo.ptPixelLocation = new POINT(x, y);
        value.pointerInfo.ptPixelLocationRaw = new POINT(x, y);
        value.touchMask = 1; // TOUCH_MASK_CONTACTAREA; pressure/orientation use system defaults.
        value.rcContact = new RECT { Left = x - CONTACT_HALF, Top = y - CONTACT_HALF,
            Right = x + CONTACT_HALF, Bottom = y + CONTACT_HALF };
        value.rcContactRaw = value.rcContact;
        return value;
    }
    private static POINTER_TOUCH_INFO[] Frame(POINT center, double radius, uint flags) {
        int offset = (int)Math.Round(radius);
        return new POINTER_TOUCH_INFO[] {
            Contact(1, center.X - offset, center.Y, flags),
            Contact(2, center.X + offset, center.Y, flags)
        };
    }
    private static POINTER_TOUCH_INFO[] EndFrame(POINTER_TOUCH_INFO[] last, bool cancel) {
        POINTER_TOUCH_INFO[] ended = (POINTER_TOUCH_INFO[])last.Clone();
        for (int i = 0; i < ended.Length; i++)
            ended[i].pointerInfo.pointerFlags = UP | (cancel ? CANCELED : 0);
        return ended;
    }
    private static bool Inject(POINTER_TOUCH_INFO[] frame, out int error) {
        error = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (InjectTouchInput(2, frame)) return true;
            error = Marshal.GetLastWin32Error();
            if (error != ERROR_NOT_READY) return false;
            Thread.Sleep(2); // The API says retry the same frame for ERROR_NOT_READY.
        }
        return false;
    }
    private static bool ModifiersReleased() {
        int[] keys = new int[] { 16, 17, 18, 91, 92 };
        for (int attempt = 0; attempt < 50; attempt++) {
            bool held = false;
            foreach (int key in keys) if ((GetAsyncKeyState(key) & 0x8000) != 0) held = true;
            if (!held) return true;
            Thread.Sleep(10);
        }
        return false;
    }
    private static int Gesture(bool enlarge) {
        // DPI selection precedes all window, cursor and injection calls.
        if (!SetProcessDPIAware() && !IsProcessDPIAware()) return 5;
        if (!LayoutValid()) return 13;
        if (!ModifiersReleased()) return 170;
        IntPtr window; POINT cursor; RECT client;
        if (!FirefoxForeground(out window) || !GetCursorPos(out cursor) || !ClientBounds(window, out client)) return 5;
        if (!PointTargetsWindow(cursor, window)) return 5;
        IntPtr monitor = MonitorFromPoint(cursor, 0);
        MONITORINFO monitorInfo = new MONITORINFO(); monitorInfo.cbSize = (uint)Marshal.SizeOf(typeof(MONITORINFO));
        if (monitor == IntPtr.Zero || !GetMonitorInfo(monitor, ref monitorInfo)) return 5;
        RECT bounds = new RECT { Left = Math.Max(client.Left, monitorInfo.rcMonitor.Left),
            Top = Math.Max(client.Top, monitorInfo.rcMonitor.Top), Right = Math.Min(client.Right, monitorInfo.rcMonitor.Right),
            Bottom = Math.Min(client.Bottom, monitorInfo.rcMonitor.Bottom) };
        int gap = CONTACT_HALF + EDGE_GAP;
        if (cursor.Y < bounds.Top + gap || cursor.Y >= bounds.Bottom - gap) return 87;
        double available = Math.Min(cursor.X - bounds.Left - gap, bounds.Right - 1 - cursor.X - gap);
        double small = Math.Min(100.0, available / 1.18);
        if (small < 24.0) return 87;
        double start = enlarge ? small : small * 1.18;
        double finish = enlarge ? small * 1.18 : small;
        if (!InitializeTouchInjection(2, 3)) { int e = Marshal.GetLastWin32Error(); return e == 0 ? 1 : e; }
        POINTER_TOUCH_INFO[] last = null;
        bool active = false, completed = false;
        int result = 0, error = 0;
        try {
            POINTER_TOUCH_INFO[] down = Frame(cursor, start, INRANGE | INCONTACT | DOWN);
            foreach (POINTER_TOUCH_INFO contact in down)
                if (!PointTargetsWindow(contact.pointerInfo.ptPixelLocation, window)) return 5;
            if (!Inject(down, out error)) return error == 0 ? 1 : error;
            active = true; last = down;
            for (int step = 1; step <= 10; step++) {
                Thread.Sleep(15);
                RECT current;
                if (GetForegroundWindow() != window || !ClientBounds(window, out current) || !SameRect(current, client)) {
                    result = ERROR_CANCELLED; break;
                }
                double progress = step / 10.0;
                double eased = progress * progress * (3.0 - 2.0 * progress);
                POINTER_TOUCH_INFO[] update = Frame(cursor, start + (finish - start) * eased, INRANGE | INCONTACT | UPDATE);
                foreach (POINTER_TOUCH_INFO contact in update)
                    if (!PointTargetsWindow(contact.pointerInfo.ptPixelLocation, window)) { result = ERROR_CANCELLED; break; }
                if (result != 0) break;
                if (!Inject(update, out error)) { result = error == 0 ? 1 : error; break; }
                last = update;
            }
            completed = result == 0;
        } finally {
            if (active && last != null) {
                // UP must use the exact last successfully injected coordinates.
                if (!Inject(EndFrame(last, !completed), out error)) {
                    int releaseError = error;
                    Inject(EndFrame(last, true), out error);
                    if (result == 0) result = releaseError == 0 ? 1 : releaseError;
                }
            }
        }
        return result;
    }
    [STAThread]
    private static int Main(string[] args) {
        if (args.Length != 1 || (args[0] != "in" && args[0] != "out")) return 87;
        bool acquired = false;
        try {
            using (Mutex gate = new Mutex(false, "Local\\MandoTouchZoom.OneShot.v1")) {
                try { acquired = gate.WaitOne(0); } catch (AbandonedMutexException) { acquired = true; }
                if (!acquired) return 170;
                try { return Gesture(args[0] == "in"); }
                finally { if (acquired) gate.ReleaseMutex(); }
            }
        } catch { return 1; }
    }
}
