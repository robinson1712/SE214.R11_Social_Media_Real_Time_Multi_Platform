"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, useTransition, type FormEvent } from "react";
import {
  ArrowRight, ArrowUpRight, Check, Eye, EyeOff, Flower2, Heart,
  LockKeyhole, Mail, Music2, ShieldCheck, Sparkles, Star, Trees,
} from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import {
  Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger,
} from "./ui/dialog";
import { initialPeople } from "@/lib/data";

const vibes = [
  { name: "Chill thôi", icon: Music2, message: "Cứ thong thả. Tụi mình ở đây." },
  { name: "Sáng tạo", icon: Sparkles, message: "Một ý tưởng hay đang chờ bạn đấy." },
  { name: "Phiêu lưu", icon: Trees, message: "Đi đâu cũng vui, miễn là có nhau." },
];
// shortcut: public demo credentials only, replace with server authentication before accepting real accounts.
const demo = { email: "hello@vibesocial.demo", password: "vibewithus" };

export function LoginScreen() {
  const router = useRouter();
  const [vibe, setVibe] = useState(0);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);
  const [error, setError] = useState("");
  const [demoFilled, setDemoFilled] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [isPending, startTransition] = useTransition();
  const passwordRef = useRef<HTMLInputElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const isPrivate = showPassword;
  const isSurprised = passwordFocused && !showPassword;

  function fillDemo() {
    setEmail(demo.email);
    setPassword(demo.password);
    setError("");
    setDemoFilled(true);
    setHelpOpen(false);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (email.trim().toLowerCase() !== demo.email || password !== demo.password) {
      setError("Bản trải nghiệm chỉ dùng tài khoản mẫu. Nhấn nút phía trên để điền nhé.");
      passwordRef.current?.focus();
      return;
    }
    setError("");
    setPassword("");
    startTransition(() => router.push("/"));
  }

  return (
    <div
        className="login-layout" lang="vi"
        onPointerMove={(event) => {
          if (event.pointerType === "touch" || !stageRef.current) return;
          const box = stageRef.current.getBoundingClientRect();
          const dx = event.clientX - (box.left + box.width / 2);
          const dy = event.clientY - (box.top + box.height / 2);
          const angle = Math.atan2(dy, dx);
          const distance = Math.min(Math.hypot(dx, dy) / 100, 1);
          event.currentTarget.style.setProperty("--eye-x", `${Math.cos(angle) * distance * 5.5}px`);
          event.currentTarget.style.setProperty("--eye-y", `${Math.sin(angle) * distance * 6.5}px`);
        }}
        onPointerLeave={(event) => {
          event.currentTarget.style.removeProperty("--eye-x");
          event.currentTarget.style.removeProperty("--eye-y");
        }}
      >
      <section
        className={`login-world vibe-${vibe}${isPrivate ? " is-private" : ""}${isSurprised ? " is-surprised" : ""}`}
        aria-labelledby="world-title"
      >
        <Link className="login-brand" href="/" aria-label="VibeSocial, về bảng tin">
          <span className="login-brand-mark"><Flower2 aria-hidden="true" /></span>
          vibe<span>social</span><span className="brand-period">.</span>
        </Link>

        <div className="world-content">
          <div className="world-eyebrow"><span /> KẾT NỐI THEO CÁCH CỦA BẠN</div>
          <h2 id="world-title">Đúng người.<br />Đúng <em>vibe.</em></h2>
          <p className="world-intro">Một chút ngẫu hứng. Một vài người bạn.<br />Và rất nhiều câu chuyện đang chờ.</p>

          <div className="vibe-stage" ref={stageRef} aria-hidden="true">
            <div className="stage-orbit" />
            <Star className="stage-star" />
            <div className="hello-bubble">{isPrivate ? "Tụi mình nhắm mắt rồi!" : isSurprised ? "Ồ, một bí mật!" : "Ê, chào bạn!"}<Heart /></div>
            <div className="friends-ground" />
            {[["blue", "sad"], ["purple", "anxious"], ["coral", "angry"], ["yellow", "joy"]].map(([color, emotion]) => (
              <div className={`vibe-friend friend-${color} friend-${emotion}`} key={color}>
                <span className="friend-hair" />
                <div className="friend-face">
                  <div className="friend-brows"><span /><span /></div>
                  <div className="friend-eyes">
                    {[0, 1].map((eye) => (
                      <span className="friend-eye" key={eye}>
                        <span className="friend-pupil" />
                      </span>
                    ))}
                  </div>
                  <span className="friend-smile" />
                  <span className="friend-surprise-mouth" />
                  <span className="friend-cheek" />
                </div>
              </div>
            ))}
            <span className="stage-sticker"><Sparkles /> be yourself.</span>
            <Flower2 className="stage-flower" />
          </div>

          <p className="world-caption" aria-live="polite">
            {isPrivate ? "Không nhìn trộm mật khẩu đâu, hứa luôn." : vibes[vibe].message}
          </p>
          <fieldset className="vibe-picker">
            <legend>Hôm nay, bạn mang vibe gì?</legend>
            <div>
              {vibes.map(({ name, icon: Icon }, index) => (
                <Button key={name} type="button" variant="ghost" aria-pressed={vibe === index}
                  onClick={() => setVibe(index)}>
                  <Icon aria-hidden="true" />{name}
                </Button>
              ))}
            </div>
          </fieldset>
        </div>

        <div className="world-footer">
          <div className="world-avatars" aria-hidden="true">
            {initialPeople.slice(1, 5).map((person) => (
              <img key={person.name} src={`/assets/${person.avatar}`} alt="" width={34} height={34} />
            ))}
          </div>
          <p>Có chỗ cho mọi phiên bản của bạn.</p>
          <Heart aria-hidden="true" />
        </div>
      </section>

      <section className="login-form-side" aria-labelledby="login-title">
        <div className="login-guest-link">Chỉ ghé thăm? <Link href="/explore">Khám phá trước <ArrowUpRight aria-hidden="true" /></Link></div>

        <div className="login-form-content">
          <div className="login-greeting" aria-hidden="true"><Sparkles /></div>
          <span className="login-kicker">MỪNG BẠN TRỞ LẠI</span>
          <h1 id="login-title">Hey, bạn đây rồi<span>!</span></h1>
          <p className="login-subtitle">Những câu chuyện hay hơn khi có bạn.</p>

          <Button type="button" variant="outline" className="login-demo-button" onClick={fillDemo}>
            {demoFilled ? <Check aria-hidden="true" /> : <Sparkles aria-hidden="true" />}
            {demoFilled ? "Tài khoản mẫu đã sẵn sàng" : "Thử với tài khoản mẫu"}
            <ArrowUpRight aria-hidden="true" />
          </Button>
          <p className="sr-only" role="status">{demoFilled ? "Đã điền email và mật khẩu mẫu. Bạn có thể đăng nhập." : ""}</p>
          <div className="login-divider"><span />hoặc nhập thông tin của bạn<span /></div>

          <form onSubmit={submit} className="login-form" aria-busy={isPending}>
            <div className="login-field">
              <label htmlFor="login-email">Email của bạn</label>
              <div className="login-input-wrap">
                <Mail aria-hidden="true" />
                <Input id="login-email" name="email" type="email" autoComplete="username"
                  placeholder="ban@vidu.com" required maxLength={254} value={email}
                  aria-invalid={!!error} aria-describedby={error ? "login-error" : undefined}
                  onChange={(event) => { setEmail(event.target.value); setError(""); setDemoFilled(false); }} />
              </div>
            </div>
            <div className="login-field">
              <div className="login-password-label">
                <label htmlFor="login-password">Mật khẩu</label>
                <Dialog open={helpOpen} onOpenChange={setHelpOpen}>
                  <DialogTrigger asChild><Button type="button" variant="link" className="login-help">Quên mất rồi?</Button></DialogTrigger>
                  <DialogContent className="login-help-dialog" lang="vi">
                    <DialogTitle>Không sao, ai cũng có lúc quên.</DialogTitle>
                    <DialogDescription>Đây là bản trải nghiệm, chưa hỗ trợ tài khoản cá nhân hoặc gửi email khôi phục. Bạn có thể dùng tài khoản mẫu để khám phá.</DialogDescription>
                    <Button type="button" onClick={fillDemo}>Điền tài khoản mẫu <ArrowRight aria-hidden="true" /></Button>
                  </DialogContent>
                </Dialog>
              </div>
              <div className="login-input-wrap"
                onFocus={() => setPasswordFocused(true)}
                onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setPasswordFocused(false); }}>
                <LockKeyhole aria-hidden="true" />
                <Input ref={passwordRef} id="login-password" name="password"
                  type={showPassword ? "text" : "password"} autoComplete="current-password"
                  placeholder="Bí mật nhỏ của bạn" required maxLength={128} value={password}
                  aria-invalid={!!error} aria-describedby={error ? "login-error login-demo-note" : "login-demo-note"}
                  onChange={(event) => { setPassword(event.target.value); setError(""); setDemoFilled(false); }} />
                <Button type="button" variant="ghost" size="icon" className="login-eye-toggle"
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"} aria-pressed={showPassword}
                  onPointerDown={(event) => { if (event.button === 0) event.preventDefault(); }}
                  onClick={() => setShowPassword((visible) => !visible)}>
                  {showPassword ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                </Button>
              </div>
            </div>
            {error && <p className="login-error" id="login-error" role="alert">{error}</p>}
            <Button type="submit" className="login-submit" disabled={isPending}>
              {isPending ? "Đang mở bảng tin..." : "Vào thế giới của bạn"}<ArrowRight aria-hidden="true" />
            </Button>
          </form>

          <p className="login-demo-note" id="login-demo-note"><ShieldCheck aria-hidden="true" />Bản trải nghiệm. Không dùng mật khẩu cá nhân nhé.</p>
          <div className="login-small-note"><span />Ít lướt vô thức. Nhiều kết nối thật hơn.</div>
        </div>

        <footer className="login-footer"><span>© 2026 VibeSocial</span><span>Made for good company <Flower2 aria-hidden="true" /></span></footer>
      </section>
    </div>
  );
}
