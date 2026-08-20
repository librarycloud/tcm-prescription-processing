using System;
using System.IO;
using System.Text;

namespace E6Sync.Services
{
    public sealed class LogService
    {
        private readonly object syncRoot = new object();
        private readonly string logPath;
        public event Action<string> MessageLogged;

        public LogService(string baseDirectory)
        {
            var directory = Path.Combine(baseDirectory, "logs");
            Directory.CreateDirectory(directory);
            logPath = Path.Combine(directory, "sync.log");
        }

        public string LogPath { get { return logPath; } }

        public void Info(string message) { Write("INFO", message); }
        public void Warn(string message) { Write("WARN", message); }
        public void Error(string message) { Write("ERROR", message); }

        private void Write(string level, string message)
        {
            var line = string.Format("[{0:yyyy-MM-dd HH:mm:ss}] [{1}] {2}", DateTime.Now, level, message ?? "");
            lock (syncRoot)
            {
                File.AppendAllText(logPath, line + Environment.NewLine, Encoding.UTF8);
            }
            var handler = MessageLogged;
            if (handler != null) handler(line);
        }
    }
}
