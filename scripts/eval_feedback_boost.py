"""离线评估：feedback-boost 对 hit-rate@5 / NDCG@5 的影响。
读取 recommendation_record 表做 leave-one-out。
"""
import argparse


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db-url", required=True)
    ap.add_argument("--window-days", type=int, default=30)
    args = ap.parse_args()
    print(f"TODO: 实现评估，读取 {args.db_url} 最近 {args.window_days} 天数据")


if __name__ == "__main__":
    main()
