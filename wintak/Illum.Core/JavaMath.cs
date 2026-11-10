using System;
using System.Collections.Generic;

namespace Illum.Core
{
    // Java rounds halves toward positive infinity; Math.Round uses bankers' rounding.
    internal static class JavaMath
    {
        internal static long round(double x) => (long)Math.Floor(x + 0.5);
        internal static double toRadians(double x) => x * Math.PI / 180.0;
        internal static double toDegrees(double x) => x * 180.0 / Math.PI;
    }
    internal static class JavaLists
    {
        internal static T RemoveAt<T>(List<T> list, int index)
        {
            T value = list[index];
            list.RemoveAt(index);
            return value;
        }
    }
}
