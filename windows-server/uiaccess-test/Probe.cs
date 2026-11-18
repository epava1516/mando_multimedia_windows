using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;

internal static class Probe {
    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool OpenProcessToken(IntPtr process, uint access, out IntPtr token);
    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool GetTokenInformation(IntPtr token, int kind, out int value, int size, out int length);
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr handle);
    [DllImport("user32.dll")]
    static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    static extern uint GetWindowThreadProcessId(IntPtr window, out int pid);
    [DllImport("user32.dll", SetLastError = true)]
    static extern uint SendInput(uint count, Input[] inputs, int size);

    [StructLayout(LayoutKind.Sequential)]
    struct Mouse { public int X, Y; public uint Data, Flags, Time; public UIntPtr Extra; }
    [StructLayout(LayoutKind.Sequential)]
    struct Key { public ushort Vk, Scan; public uint Flags, Time; public UIntPtr Extra; }
    [StructLayout(LayoutKind.Explicit)]
    struct Payload {
        [FieldOffset(0)] public Mouse Mouse;
        [FieldOffset(0)] public Key Key;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct Input { public uint Type; public Payload Payload; }

    static void Main() {
        int value = 0, length;
        using (Process self = Process.GetCurrentProcess()) {
            IntPtr token;
            if (OpenProcessToken(self.Handle, 8, out token)) {
                try { GetTokenInformation(token, 26, out value, sizeof(int), out length); }
                finally { CloseHandle(token); }
            }
        }
        Console.WriteLine("TokenUIAccess = " + value);
        if (value == 1) {
            Console.WriteLine("Pulsa Intro y enfoca el Administrador de tareas en los siguientes cinco segundos.");
            Console.ReadLine();
            Thread.Sleep(5000);
            IntPtr window = GetForegroundWindow();
            int pid;
            if (window != IntPtr.Zero && GetWindowThreadProcessId(window, out pid) != 0) {
                using (Process target = Process.GetProcessById(pid)) {
                    if (String.Equals(target.ProcessName, "Taskmgr", StringComparison.OrdinalIgnoreCase)
                        && GetForegroundWindow() == window) {
                        Input[] events = {
                            new Input { Type = 1, Payload = new Payload { Key = new Key { Vk = 0x74 } } },
                            new Input { Type = 1, Payload = new Payload { Key = new Key { Vk = 0x74, Flags = 2 } } }
                        };
                        uint inserted = SendInput(2, events, Marshal.SizeOf(typeof(Input)));
                        Console.WriteLine("F5: eventos aceptados " + inserted + "/2");
                    } else Console.WriteLine("El Administrador de tareas no tenia el foco.");
                }
            }
        }
        Console.WriteLine("Pulsa Intro para cerrar.");
        Console.ReadLine();
    }
}
