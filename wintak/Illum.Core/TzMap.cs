using System;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace Illum.Core
{
    // Reads the unchanged Android ODbL timezone-boundary-builder grid (big endian, zlib).
    public sealed class TzMap
    {
        private readonly int cols, rows;
        private readonly double resolution;
        private readonly string[] zones;
        private readonly ushort[][] starts, indices;
        private TzMap(int cols, int rows, double res, string[] zones, ushort[][] starts, ushort[][] indices)
        { this.cols = cols; this.rows = rows; resolution = res; this.zones = zones; this.starts = starts; this.indices = indices; }
        public static TzMap LoadEmbedded()
        {
            var stream = typeof(TzMap).Assembly.GetManifestResourceStream("Illum.tzmap.bin");
            if (stream == null) throw new InvalidDataException("Embedded time zone map is missing.");
            return read(stream);
        }
        public static TzMap read(Stream raw)
        {
            using (raw)
            {
                // DeflateStream on .NET Framework expects raw DEFLATE, not the zlib header.
                int cmf = raw.ReadByte(), flg = raw.ReadByte();
                if (cmf < 0 || flg < 0 || (cmf & 15) != 8 || ((cmf << 8) + flg) % 31 != 0 || (flg & 32) != 0)
                    throw new InvalidDataException("Invalid time zone map compression header.");
                using (var deflate = new DeflateStream(raw, CompressionMode.Decompress, true))
                using (var reader = new BinaryReader(deflate, Encoding.UTF8))
                {
                    if (U16(reader) != 0x4954 || U16(reader) != 0x5A31) throw new InvalidDataException("Invalid time zone map magic.");
                    int cols = U16(reader), rows = U16(reader);
                    byte[] bytes = reader.ReadBytes(4); if (bytes.Length != 4) throw new EndOfStreamException();
                    if (BitConverter.IsLittleEndian) Array.Reverse(bytes);
                    double res = BitConverter.ToSingle(bytes, 0);
                    if (!(res > 0) || Math.Abs(cols * res - 360) > 0.001 || Math.Abs(rows * res - 180) > 0.001)
                        throw new InvalidDataException("Invalid time zone grid dimensions.");
                    var zones = new string[U16(reader)];
                    for (int i = 0; i < zones.Length; i++)
                    { int length = reader.ReadByte(); bytes = reader.ReadBytes(length); if (bytes.Length != length) throw new EndOfStreamException(); zones[i] = Encoding.UTF8.GetString(bytes); }
                    var starts = new ushort[rows][]; var indices = new ushort[rows][];
                    for (int r = 0; r < rows; r++)
                    {
                        int runs = U16(reader); starts[r] = new ushort[runs]; indices[r] = new ushort[runs];
                        for (int k = 0; k < runs; k++)
                        {
                            starts[r][k] = (ushort)U16(reader); indices[r][k] = (ushort)U16(reader);
                            if (starts[r][k] >= cols || k > 0 && starts[r][k] <= starts[r][k - 1]) throw new InvalidDataException("Invalid time zone row.");
                        }
                    }
                    return new TzMap(cols, rows, res, zones, starts, indices);
                }
            }
        }
        private static int U16(BinaryReader reader) => (reader.ReadByte() << 8) | reader.ReadByte();
        public int zoneCount() => zones.Length;
        public string zoneAt(double lat, double lon)
        {
            if (double.IsNaN(lat) || double.IsNaN(lon) || double.IsInfinity(lat) || double.IsInfinity(lon)) return null;
            int r = Math.Max(0, Math.Min(rows - 1, (int)Math.Floor((90 - lat) / resolution)));
            int c = Math.Max(0, Math.Min(cols - 1, (int)Math.Floor(((lon + 180) % 360 + 360) % 360 / resolution)));
            int lo = 0, hi = starts[r].Length - 1; if (hi < 0) return null;
            while (lo < hi) { int mid = (lo + hi + 1) / 2; if (starts[r][mid] <= c) lo = mid; else hi = mid - 1; }
            int index = indices[r][lo]; return index >= zones.Length ? null : zones[index];
        }
    }
}
