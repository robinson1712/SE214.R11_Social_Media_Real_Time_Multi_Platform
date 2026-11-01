import Link from "next/link";
export default function NotFound() {
  return (
    <div className="empty-state">
      <h2>This page could not be found</h2>
      <p>The link may have changed. Your feed is a good place to start.</p>
      <Link className="primary-btn" href="/">
        Back to feed
      </Link>
    </div>
  );
}
