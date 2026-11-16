package com.zframe.inject;

/** v2.13：分三个版本打包（build 时改 ED）。0 = 单机版（大厅自建服务端、仓库衣服、人机）；
 *  1 = 联机房主版（只有开房，不开自建服务端、不发衣服）；2 = 联机客户端版（只有加入，同样不开服务端）。 */
final class Ed {
    static final int ED = 0;
    static boolean single() { return ED == 0; }
    static String name() { return ED == 1 ? "房主版" : ED == 2 ? "客户端版" : "单机版"; }
}
