package com.termoak.app.store

/** The demo terminals' screens: ANSI text for a size (columns, rows). */
object TermDemo {
    private const val R = "\u001b[0m"
    private const val B = "\u001b[1m"
    private fun c(n: Int) = "\u001b[${n}m"
    private fun c256(n: Int) = "\u001b[38;5;${n}m"

    private fun prompt(user: String, host: String, dir: String) = "$B${c(32)}$user@$host$R:$B${c(34)}$dir$R$ "

    /** web-1: login banner, `ls --color`, `git log`, `systemctl status` and the prompt. */
    fun web1(cols: Int, rows: Int): List<String> {
        val wide = cols >= 70
        val p = prompt("ane", "web-1", "/srv/shop")
        val narrow = cols < 60
        val lines = mutableListOf(
            "Last login: Mon Oct 12 08:02:11 2026 from 10.0.0.5",
            if (narrow) "Welcome to Ubuntu 24.04.1 LTS" else "Welcome to Ubuntu 24.04.1 LTS (GNU/Linux 6.8.0-45-generic x86_64)",
            "",
            if (narrow) "  System load: 0.42    Processes: 187" else "  System load:  0.42        Processes:       187",
            if (narrow) "  Usage of /:  38.1%   Memory:    41%" else "  Usage of /:   38.1%       Users logged in: 1",
            if (narrow) "  IPv4 for eth0: 10.0.1.11" else "  Memory usage: 41%         IPv4 for eth0:   10.0.1.11",
            "",
            "${prompt("ane", "web-1", "~")}cd /srv/shop && ls",
            "${c(34)}${B}config$R  ${c(34)}${B}deploy$R  ${c(32)}${B}deploy.sh$R  docker-compose.yml",
            "${c(34)}${B}logs$R    ${c(34)}${B}public$R  ${c(31)}${B}backup.tar.gz$R  nginx.conf",
            "${p}git log --oneline --graph -5",
            "${c(33)}* 3f9c2a1$R ${c(33)}(${B}${c(36)}HEAD -> ${c(32)}main$R${c(33)})$R Cache images",
            "${c(33)}* 8b41d07$R Add /healthz endpoint",
            "${c(31)}|$R${c(32)}\\$R  ",
            "${c(31)}|$R ${c(33)}* e1a6c55$R ${c(33)}(${c(31)}origin/checkout$R${c(33)})$R New checkout",
            "${c(31)}|$R${c(32)}/$R  ",
            "${c(33)}* 5d02b9e$R Bump nginx to 1.26",
            "${p}systemctl status nginx",
            "${B}${c(32)}●$R nginx.service - nginx web server",
            "   Loaded: loaded; ${B}${c(32)}enabled$R",
            "   Active: ${B}${c(32)}active (running)$R 4h 16min ago",
            "   Memory: 18.4M  Tasks: 5",
            "${p}curl -sI :80/healthz | head -1",
            "HTTP/1.1 ${B}${c(32)}200 OK$R",
            "${p}docker compose ps",
            "${B}NAME         STATUS$R",
            "shop-web     ${c(32)}Up 4 hours (healthy)$R",
            "shop-worker  ${c(32)}Up 4 hours$R",
            "shop-redis   ${c(32)}Up 4 hours$R",
            "${p}tail -n 3 logs/access.log",
            "\"GET /api/cart\" ${c(32)}200$R 512 ${c(90)}12ms$R",
            "\"POST /api/order\" ${c(32)}201$R 87 ${c(90)}48ms$R",
            "\"GET /img/logo.svg\" ${c(36)}304$R 0 ${c(90)}2ms$R",
            p,
        )
        return lines
    }

    private fun bar(label: String, width: Int, parts: List<Pair<Int, Double>>, text: String): String {
        val inner = (width - label.length - 2).coerceAtLeast(10)
        val sb = StringBuilder("${c(36)}$label$R$B[$R")
        var used = 0
        for ((color, frac) in parts) {
            val n = (frac * inner).toInt().coerceAtMost(inner - used)
            sb.append(c(color)).append("|".repeat(n)).append(R)
            used += n
        }
        val room = inner - used
        sb.append(" ".repeat((room - text.length).coerceAtLeast(0))).append("${c(90)}$text$R".takeIf { room >= text.length } ?: "")
        sb.append("$B]$R")
        return sb.toString()
    }

    /** web-1 in `htop`: CPU and memory bars, the summary and the processes. */
    fun htop(cols: Int, rows: Int): List<String> {
        val half = if (cols >= 90) cols / 2 - 1 else cols
        val left = listOf(
            bar("  0", half, listOf(32 to 0.22, 31 to 0.08), "31.4%"),
            bar("  1", half, listOf(32 to 0.14, 31 to 0.05), "19.0%"),
            bar("  2", half, listOf(32 to 0.40, 31 to 0.12, 34 to 0.03), "55.8%"),
            bar("  3", half, listOf(32 to 0.09, 31 to 0.03), "12.2%"),
            bar("Mem", half, listOf(32 to 0.31, 34 to 0.06, 33 to 0.12), "3.12G/7.76G"),
            bar("Swp", half, listOf(31 to 0.01), "12.0M/2.00G"),
        )
        val right = listOf(
            "  ${c(36)}Tasks: $B${c(36)}87$R${c(36)}, $B${c(32)}213$R${c(36)} thr; $B${c(32)}2$R${c(36)} running$R",
            "  ${c(36)}Load average: $B${c(36)}0.42 $R${c(36)}0.38 0.31$R",
            "  ${c(36)}Uptime: $B${c(36)}12 days, 04:12:55$R",
            "",
            "",
            "",
        )
        val head = if (cols >= 90) left.zip(right) { l, r -> l + " " + r } else left + right.take(3)
        val header = "    PID USER       PRI  NI  VIRT   RES   SHR S  CPU% MEM%   TIME+  Command"
        val procs = listOf(
            Triple("   1423 www-data    20   0  412M 96.2M 12.1M ", "S", " 12.4  1.2  3:41.07 nginx: worker process"),
            Triple("   2210 ane         20   0 1.21G  412M 38.6M ", "R", "  9.8  5.2  9:02.11 node /srv/shop/server.js"),
            Triple("   1424 www-data    20   0  412M 94.0M 12.1M ", "S", "  6.1  1.2  3:12.88 nginx: worker process"),
            Triple("    988 redis       20   0  186M 41.3M  7.9M ", "S", "  2.3  0.5  1:55.40 redis-server *:6379"),
            Triple("   3311 ane         20   0 11.4M  5.1M  3.4M ", "R", "  1.1  0.1  0:00.42 htop"),
            Triple("    702 root        20   0  1.8G 52.7M 30.1M ", "S", "  0.9  0.7  0:48.10 /usr/bin/containerd"),
            Triple("      1 root        20   0  166M 13.2M  8.6M ", "S", "  0.0  0.2  0:07.93 /sbin/init"),
            Triple("    641 root        20   0 15.4M  7.0M  6.0M ", "S", "  0.0  0.1  0:00.21 sshd: /usr/sbin/sshd -D"),
            Triple("   3290 ane         20   0 17.9M 10.5M  8.2M ", "S", "  0.0  0.1  0:00.08 sshd: ane@pts/0"),
            Triple("    815 root        20   0 23.5M  6.2M  5.4M ", "S", "  0.0  0.1  0:00.03 /usr/sbin/cron -f"),
            Triple("    530 systemd+    20   0 25.6M 12.9M  9.3M ", "S", "  0.0  0.2  0:01.12 systemd-resolved"),
            Triple("    912 postfix     20   0 43.1M  6.4M  5.6M ", "S", "  0.0  0.1  0:00.11 qmgr -l -t unix -u"),
            Triple("    907 postfix     20   0 43.0M  6.1M  5.3M ", "S", "  0.0  0.1  0:00.04 pickup -l -t unix -u"),
            Triple("    598 root        20   0  289M 11.2M  9.0M ", "S", "  0.0  0.1  0:02.67 /usr/libexec/udisks2/udisksd"),
            Triple("    611 syslog      20   0  217M  5.9M  4.6M ", "S", "  0.0  0.1  0:00.92 /usr/sbin/rsyslogd -n -iNONE"),
            Triple("   1141 root        20   0 55.4M  1.6M  0.2M ", "S", "  0.0  0.0  0:00.00 nginx: master process"),
            Triple("    980 redis       20   0  186M 41.3M  7.9M ", "S", "  0.0  0.5  0:12.03 redis-server *:6379 [bgsave]"),
            Triple("   2211 ane         20   0 1.21G  412M 38.6M ", "S", "  0.0  5.2  1:20.48 node /srv/shop/server.js"),
            Triple("   2212 ane         20   0 1.21G  412M 38.6M ", "S", "  0.0  5.2  1:18.90 node /srv/shop/server.js"),
            Triple("    455 root        20   0 33.1M  9.8M  7.4M ", "S", "  0.0  0.1  0:00.66 /usr/lib/systemd/systemd-udevd"),
            Triple("    690 root        20   0  108M 21.4M 13.2M ", "S", "  0.0  0.3  0:01.71 /usr/bin/python3 /usr/bin/unattended-upgr"),
            Triple("    720 root        20   0 1.64G 31.8M 18.0M ", "S", "  0.0  0.4  0:20.17 /usr/bin/dockerd -H fd://"),
            Triple("    724 root        20   0  1.2G 14.9M 10.2M ", "S", "  0.0  0.2  0:05.32 /usr/bin/containerd-shim-runc-v2"),
            Triple("    833 root        20   0  7.2M  2.4M  2.2M ", "S", "  0.0  0.0  0:00.01 /sbin/agetty -o -p -- \\u --noclear tty1"),
            Triple("    529 systemd+    20   0 17.9M  7.6M  6.7M ", "S", "  0.0  0.1  0:00.19 /usr/lib/systemd/systemd-networkd"),
            Triple("    989 redis       20   0  186M 41.3M  7.9M ", "S", "  0.0  0.5  0:03.11 redis-server *:6379 [io]"),
            Triple("    531 systemd+    20   0 25.6M 12.9M  9.3M ", "S", "  0.0  0.2  0:00.33 /usr/lib/systemd/systemd-timesyncd"),
            Triple("    301 root        20   0 64.8M 19.0M 18.1M ", "S", "  0.0  0.2  0:01.40 /usr/lib/systemd/systemd-journald"),
            Triple("   3331 ane         20   0  9.8M  5.4M  3.5M ", "S", "  0.0  0.1  0:00.02 -bash"),
            Triple("   1425 www-data    20   0  412M 92.8M 12.1M ", "S", "  0.0  1.2  2:58.31 nginx: worker process"),
            Triple("   1426 www-data    20   0  412M 91.1M 12.1M ", "S", "  0.0  1.2  2:41.09 nginx: worker process"),
        )
        val out = mutableListOf<String>()
        out += head
        out += ""
        out += "\u001b[30;42m" + header.padEnd(cols) + R
        procs.take((rows - out.size - 2).coerceAtLeast(0)).forEachIndexed { i, (a, s, b) ->
            val st = if (s == "R") "$B${c(32)}R$R" else s
            val line = a + st + b
            out += if (i == 1) "\u001b[30;46m" + Ansi.plain(line).padEnd(cols) + R else line
        }
        while (out.size < rows - 1) out += ""
        val keys = listOf("F1" to "Help", "F2" to "Setup", "F3" to "Search", "F4" to "Filter", "F5" to "Tree", "F6" to "SortBy", "F9" to "Kill", "F10" to "Quit")
        out += keys.joinToString("") { (k, v) -> "$R$k\u001b[30;46m${v.padEnd(6)}" } + R
        return out.take(rows)
    }

    /** The same command typed in every pane (broadcast), after each host's own history. */
    fun broadcast(host: String, cols: Int, rows: Int): List<String> {
        val p = prompt("ane", host, "~")
        val history = when (host) {
            "web-1", "web-2" -> listOf(
                "${p}sudo nginx -t",
                "nginx: the configuration file /etc/nginx/nginx.conf syntax is ${c(32)}ok$R",
                "nginx: configuration file /etc/nginx/nginx.conf test is ${B}${c(32)}successful$R",
                "${p}df -h /",
                "Filesystem      Size  Used Avail Use% Mounted on",
                "/dev/vda1        40G   15G   24G  ${c(32)}38%$R /",
            )
            "db-primary" -> listOf(
                "${prompt("postgres", host, "~")}psql -c 'select now()-pg_last_xact_replay_timestamp() as lag'",
                "       lag",
                "-----------------",
                " 00:00:00.041327",
                "(1 row)",
                "",
                "${p}df -h /var/lib/postgresql",
                "Filesystem      Size  Used Avail Use% Mounted on",
                "/dev/vdb1       200G  118G   82G  ${c(33)}59%$R /var/lib/postgresql",
            )
            "api-1" -> listOf(
                "${p}systemctl status shop-api --no-pager | head -3",
                "${B}${c(32)}●$R shop-api.service - Shop API",
                "     Loaded: loaded (/etc/systemd/system/shop-api.service; ${B}${c(32)}enabled$R)",
                "     Active: ${B}${c(32)}active (running)$R since Mon 2026-10-12 12:31:02 UTC; 2min ago",
                "${p}curl -s localhost:8080/healthz",
                "{\"status\":\"${c(32)}ok${R}\",\"db\":\"${c(32)}ok${R}\",\"redis\":\"${c(32)}ok${R}\"}",
            )
            else -> listOf(
                "${p}docker ps --format '{{.Names}}\\t{{.Status}}'",
                "grafana     ${c(32)}Up 12 days$R",
                "prometheus  ${c(32)}Up 12 days$R",
                "loki        ${c(32)}Up 12 days$R",
                "alertmanager ${c(33)}Up 3 hours$R",
                "${p}uptime",
                " 12:30:11 up 12 days,  4:12,  1 user,  load average: 0.21, 0.18, 0.12",
            )
        }
        val banner = when (host) {
            "db-primary", "api-1" -> "Debian GNU/Linux 12 (bookworm)"
            "monitoring" -> "Welcome to Alpine Linux 3.20"
            else -> "Welcome to Ubuntu 24.04.1 LTS"
        }
        return listOf(banner, "") + history + "${p}sudo apt update && sudo apt upgrade -y"
    }

    /** backup: a long-running job with coloured progress. */
    fun backup(cols: Int, rows: Int): List<String> {
        val p = prompt("ane", "backup", "~")
        return listOf(
            "${p}restic snapshots --latest 3",
            "${c(33)}ID        Time                 Host    Tags    Paths$R",
            "----------------------------------------------------------",
            "4e1d2c3a  2026-10-10 03:30:02  web-1   nightly /srv/shop",
            "9a7b6c5d  2026-10-11 03:30:01  web-1   nightly /srv/shop",
            "1f2e3d4c  2026-10-12 03:30:04  web-1   nightly /srv/shop",
            "----------------------------------------------------------",
            "3 snapshots",
            "${p}df -h /",
            "Filesystem      Size  Used Avail Use% Mounted on",
            "/dev/sda2        80G   74G  6.1G  ${B}${c(31)}93%$R /",
            p,
        )
    }

    /** web-1 with a failing upstream, the context the copilot is asked about. */
    fun web1Logs(cols: Int, rows: Int): List<String> {
        val p = prompt("ane", "web-1", "/srv/shop")
        return listOf(
            "${p}curl -sI localhost/api/cart | head -1",
            "HTTP/1.1 ${B}${c(31)}502 Bad Gateway$R",
            "${p}sudo tail -n 4 /var/log/nginx/error.log",
            "2026/10/12 12:27:41 ${c(31)}[error]$R 1423#1423: *8812 connect() failed (111: Connection refused) while connecting to upstream, upstream: \"http://127.0.0.1:8080/api/cart\"",
            "2026/10/12 12:28:03 ${c(31)}[error]$R 1424#1424: *8840 connect() failed (111: Connection refused) while connecting to upstream, upstream: \"http://127.0.0.1:8080/api/cart\"",
            "${p}systemctl is-active shop-api",
            "${c(31)}failed$R",
            p,
        )
    }
}
