#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Android High-Precision Real-Time FPS Monitor CLI
Supports ADB execution (from PC/Mac/Linux) and on-device execution (via Termux with root / termux-adb).
"""

import sys
import os
import time
import subprocess
import argparse
import collections
import re

# ANSI Color Codes
RESET = "\033[0m"
BOLD = "\033[1m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
ORANGE = "\033[38;5;208m"
RED = "\033[31m"
CYAN = "\033[36m"
BLUE = "\033[34m"
GRAY = "\033[90m"
CLEAR_SCREEN = "\033[2J\033[H"

class AdbFpsMonitor:
    def __init__(self, device_id=None, surface_name=None, poll_interval=0.2, window_size=120, csv_file=None):
        self.device_id = device_id
        self.surface_name = surface_name
        self.poll_interval = poll_interval
        self.window_size = window_size
        self.csv_file = csv_file
        self.is_termux = "TERMUX_VERSION" in os.environ or os.path.exists("/data/data/com.termux")
        
        self.frame_durations = collections.deque(maxlen=window_size)
        self.all_frame_durations = []
        self.last_present_time_ns = 0
        self.total_frames = 0
        self.stutter_frames = 0
        self.start_time = None
        
        self.csv_handle = None
        if self.csv_file:
            self.csv_handle = open(self.csv_file, "w", encoding="utf-8")
            self.csv_handle.write("Timestamp,Surface,FPS,FrameTime_ms,Avg_FPS,1Percent_Low_FPS,Stutter_Rate\n")

    def run_cmd(self, cmd_args):
        if self.is_termux and not self.device_id:
            # Running directly on Android device
            # Check if root is available
            try:
                full_cmd = " ".join(cmd_args)
                res = subprocess.run(["su", "-c", full_cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=3)
                if res.returncode == 0:
                    return res.stdout
            except Exception:
                pass
            # Fallback to direct execution
            try:
                res = subprocess.run(cmd_args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=3)
                return res.stdout
            except Exception:
                return ""
        else:
            # Running via ADB
            adb_base = ["adb"]
            if self.device_id:
                adb_base.extend(["-s", self.device_id])
            adb_base.extend(["shell"])
            adb_base.extend(cmd_args)
            try:
                res = subprocess.run(adb_base, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=3)
                return res.stdout
            except Exception:
                return ""

    def detect_active_surface(self):
        output = self.run_cmd(["dumpsys", "window", "|", "grep", "-E", "'mCurrentFocus|mFocusedApp'"])
        for line in output.splitlines():
            if "mCurrentFocus" in line:
                match = re.search(r'mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)', line)
                if match:
                    return match.group(1).strip()
        return "Global"

    def fetch_latency(self):
        surface = self.surface_name or self.detect_active_surface()
        if surface and surface != "Global":
            out = self.run_cmd(["dumpsys", "SurfaceFlinger", "--latency", f'"{surface}"'])
        else:
            out = self.run_cmd(["dumpsys", "SurfaceFlinger", "--latency"])
        return surface, out

    def parse_latency_data(self, raw_output):
        lines = [line.strip() for line in raw_output.splitlines() if line.strip()]
        if len(lines) < 2:
            return

        try:
            refresh_period_ns = int(lines[0])
        except ValueError:
            refresh_period_ns = 16666666

        prev_present_time = self.last_present_time_ns

        for line in lines[1:]:
            tokens = line.split()
            if len(tokens) < 3:
                continue

            try:
                desired_time = int(tokens[0])
                actual_time = int(tokens[1])
                ready_time = int(tokens[2])
            except ValueError:
                continue

            # Check for invalid/pending frame timestamps (0 or Long.MAX_VALUE)
            if actual_time <= 0 or actual_time >= 9223372036854775807:
                continue

            if actual_time > prev_present_time:
                if prev_present_time > 0:
                    delta_ns = actual_time - prev_present_time
                    delta_ms = delta_ns / 1_000_000.0
                    # Sanity filter: 0.5ms (2000fps) to 500ms (2fps)
                    if 0.5 <= delta_ms <= 500.0:
                        self.frame_durations.append(delta_ms)
                        self.all_frame_durations.append(delta_ms)
                        self.total_frames += 1
                        if delta_ms > 33.33: # Frame lag threshold (> 2 standard 60fps frames)
                            self.stutter_frames += 1
                prev_present_time = actual_time

        if prev_present_time > self.last_present_time_ns:
            self.last_present_time_ns = prev_present_time

    def calculate_stats(self):
        if not self.frame_durations:
            return None

        current_ft = self.frame_durations[-1]
        current_fps = 1000.0 / current_ft if current_ft > 0 else 0.0

        avg_ft = sum(self.frame_durations) / len(self.frame_durations)
        avg_fps = 1000.0 / avg_ft if avg_ft > 0 else 0.0

        fps_list = [1000.0 / ft for ft in self.frame_durations if ft > 0]
        min_fps = min(fps_list) if fps_list else 0.0
        max_fps = max(fps_list) if fps_list else 0.0

        sorted_fts = sorted(list(self.frame_durations))
        idx_1pct = min(len(sorted_fts) - 1, int(len(sorted_fts) * 0.99))
        low_1pct_ft = sorted_fts[idx_1pct]
        low_1pct_fps = 1000.0 / low_1pct_ft if low_1pct_ft > 0 else 0.0

        idx_01pct = min(len(sorted_fts) - 1, int(len(sorted_fts) * 0.999))
        low_01pct_ft = sorted_fts[idx_01pct]
        low_01pct_fps = 1000.0 / low_01pct_ft if low_01pct_ft > 0 else 0.0

        stutter_rate = (self.stutter_frames / self.total_frames * 100.0) if self.total_frames > 0 else 0.0

        return {
            "fps": current_fps,
            "frametime_ms": current_ft,
            "avg_fps": avg_fps,
            "min_fps": min_fps,
            "max_fps": max_fps,
            "low_1pct_fps": low_1pct_fps,
            "low_01pct_fps": low_01pct_fps,
            "stutter_rate": stutter_rate,
            "total_frames": self.total_frames
        }

    def render_bar(self, fps, target_fps=120, width=25):
        ratio = min(1.0, max(0.0, fps / target_fps))
        filled_len = int(width * ratio)
        bar = "█" * filled_len + "░" * (width - filled_len)
        if fps >= target_fps * 0.95:
            return f"{GREEN}{bar}{RESET}"
        elif fps >= target_fps * 0.70:
            return f"{YELLOW}{bar}{RESET}"
        elif fps >= target_fps * 0.50:
            return f"{ORANGE}{bar}{RESET}"
        else:
            return f"{RED}{bar}{RESET}"

    def run(self):
        print(f"{CYAN}Starting Android FPS Monitor... Press Ctrl+C to stop.{RESET}")
        self.start_time = time.time()

        try:
            while True:
                surface, raw_latency = self.fetch_latency()
                self.parse_latency_data(raw_latency)
                stats = self.calculate_stats()

                if stats:
                    fps = stats["fps"]
                    fps_color = GREEN if fps >= 58 else (YELLOW if fps >= 40 else RED)
                    bar_view = self.render_bar(fps, target_fps=120, width=20)

                    output = [
                        CLEAR_SCREEN,
                        f"{BOLD}╔════════════════════════════════════════════════════════════════════╗{RESET}",
                        f"{BOLD}║             🚀 ANDROID REAL-TIME FPS & PERFORMANCE MONITOR         ║{RESET}",
                        f"{BOLD}╠════════════════════════════════════════════════════════════════════╣{RESET}",
                        f"║ {CYAN}Target Surface:{RESET} {surface[:50]:<50} ║",
                        f"║ {CYAN}Elapsed Time  :{RESET} {time.time() - self.start_time:>6.1f} s {'':<41} ║",
                        f"{BOLD}╠════════════════════════════════════════════════════════════════════╣{RESET}",
                        f"║  Realtime FPS : {fps_color}{BOLD}{fps:>6.1f} FPS{RESET}  [{bar_view}]            ║",
                        f"║  Frame Time   : {BOLD}{stats['frametime_ms']:>6.2f} ms{RESET} {'':<43} ║",
                        f"{BOLD}╠════════════════════════════════════════════════════════════════════╣{RESET}",
                        f"║  Average FPS  : {stats['avg_fps']:>6.1f} FPS   |   Min / Max FPS: {stats['min_fps']:>5.1f} / {stats['max_fps']:>5.1f}   ║",
                        f"║  1% Low FPS   : {YELLOW}{stats['low_1pct_fps']:>6.1f} FPS{RESET}   |   0.1% Low FPS : {ORANGE}{stats['low_01pct_fps']:>6.1f} FPS{RESET}   ║",
                        f"║  Stutter Rate : {RED if stats['stutter_rate'] > 1 else GREEN}{stats['stutter_rate']:>5.2f}%{RESET}     |   Total Frames : {stats['total_frames']:<15} ║",
                        f"{BOLD}╚════════════════════════════════════════════════════════════════════╝{RESET}",
                        f"{GRAY}Tips: Click inside the app or game to see real-time render latency updates.{RESET}"
                    ]
                    print("\n".join(output))

                    if self.csv_handle:
                        now_str = time.strftime("%Y-%m-%d %H:%M:%S")
                        self.csv_handle.write(f"{now_str},{surface},{fps:.2f},{stats['frametime_ms']:.2f},{stats['avg_fps']:.2f},{stats['low_1pct_fps']:.2f},{stats['stutter_rate']:.2f}\n")
                        self.csv_handle.flush()

                time.sleep(self.poll_interval)
        except KeyboardInterrupt:
            print(f"\n{CYAN}Monitoring stopped by user.{RESET}")
            if self.csv_handle:
                self.csv_handle.close()
                print(f"{GREEN}Session metrics saved to {self.csv_file}{RESET}")

def main():
    parser = argparse.ArgumentParser(description="Android Real-time FPS Monitor Tool")
    parser.add_argument("-s", "--serial", help="ADB Device Serial Number", default=None)
    parser.add_argument("-w", "--window", help="Specific target window/surface name", default=None)
    parser.add_argument("-i", "--interval", type=float, default=0.2, help="Sampling interval in seconds (default: 0.2)")
    parser.add_argument("-o", "--output", help="Save metrics to CSV file", default=None)
    args = parser.parse_args()

    monitor = AdbFpsMonitor(
        device_id=args.serial,
        surface_name=args.window,
        poll_interval=args.interval,
        csv_file=args.output
    )
    monitor.run()

if __name__ == "__main__":
    main()
