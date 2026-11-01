"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  ArrowRight, ArrowUpRight, Eye, EyeOff, Flower2, Heart,
  LockKeyhole, Mail, Music2, ShieldCheck, Sparkles, Star, Trees,
} from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import {
  Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger,
} from "./ui/dialog";
import { initialPeople } from "@/lib/data";
import { useSocial } from "./social-provider";

const vibes = [
  { name: "Chill thôi", icon: Music2, message: "Cứ thong thả. Tụi mình ở đây." },
  { name: "Sáng tạo", icon: Sparkles, message: "Một ý tưởng hay đang chờ bạn đấy." },
  { name: "Phiêu lưu", icon: Trees, message: "Đi đâu cũng vui, miễn là có nhau." },
];
export function LoginScreen() {
  const router = useRouter();
  const { login, register, session, ready } = useSocial();
  const [vibe, setVibe] = useState(0);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);
  const [error, setError] = useState("");
  const [registering, setRegistering] = useState(false);
  const [fullName, setFullName] = useState("");
  const [helpOpen, setHelpOpen] = useState(false);
  const [isPending, setPending] = useState(false);
  const submitting = useRef(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const isPrivate = showPassword;
  const isSurprised = passwordFocused && !showPassword;

  useEffect(() => { if (ready && session) router.replace("/"); }, [ready, session, router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    setPending(true);
    setError("");
    try {
      if (registering) await register({ email: email.trim(), password, fullName: fullName.trim() });
      else await login(email.trim(), password);
      setPassword("");
      router.replace("/");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể kết nối. Hãy thử lại.");
      passwordRef.current?.focus();
    } finally { submitting.current = false; setPending(false); }
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
          <span className="login-kicker">{registering ? "CÙNG KẾT NỐI" : "MỪNG BẠN TRỞ LẠI"}</span>
          <h1 id="login-title">{registering ? "Tạo tài khoản của bạn" : "Hey, bạn đây rồi"}<span>!</span></h1>
          <p className="login-subtitle">Những câu chuyện hay hơn khi có bạn.</p>

          <Button type="button" variant="outline" className="login-demo-button" disabled={isPending} onClick={() => { setRegistering(!registering); setError(""); }}>
            <Sparkles aria-hidden="true" />
            {registering ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký"}
            <ArrowUpRight aria-hidden="true" />
          </Button>
          <div className="login-divider"><span />nhập thông tin của bạn<span /></div>

          <form onSubmit={submit} className="login-form" aria-busy={isPending}>
            {registering && <div className="login-field"><label htmlFor="login-full-name">Họ và tên</label><div className="login-input-wrap"><Input id="login-full-name" name="fullName" autoComplete="name" required maxLength={100} value={fullName} disabled={isPending} onChange={(event) => setFullName(event.target.value)} /></div></div>}
            <div className="login-field">
              <label htmlFor="login-email">Email của bạn</label>
              <div className="login-input-wrap">
                <Mail aria-hidden="true" />
                <Input id="login-email" name="email" type="email" autoComplete="username"
                  placeholder="ban@vidu.com" required maxLength={254} value={email} disabled={isPending}
                  aria-invalid={!!error} aria-describedby={error ? "login-error" : undefined}
                  onChange={(event) => { setEmail(event.target.value); setError(""); }} />
              </div>
            </div>
            <div className="login-field">
              <div className="login-password-label">
                <label htmlFor="login-password">Mật khẩu</label>
                <Dialog open={helpOpen} onOpenChange={setHelpOpen}>
                  <DialogTrigger asChild><Button type="button" variant="link" className="login-help">Quên mất rồi?</Button></DialogTrigger>
                  <DialogContent className="login-help-dialog" lang="vi">
                    <DialogTitle>Không sao, ai cũng có lúc quên.</DialogTitle>
                    <DialogDescription>Hệ thống hiện chưa có API khôi phục mật khẩu. Liên hệ quản trị viên để được hỗ trợ tài khoản.</DialogDescription>
                    <Button type="button" onClick={() => setHelpOpen(false)}>Đóng</Button>
                  </DialogContent>
                </Dialog>
              </div>
              <div className="login-input-wrap"
                onFocus={() => setPasswordFocused(true)}
                onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setPasswordFocused(false); }}>
                <LockKeyhole aria-hidden="true" />
                <Input ref={passwordRef} id="login-password" name="password"
                  type={showPassword ? "text" : "password"} autoComplete={registering ? "new-password" : "current-password"}
                  placeholder="Bí mật nhỏ của bạn" required minLength={registering ? 8 : undefined} maxLength={128} value={password} disabled={isPending}
                  aria-invalid={!!error} aria-describedby={error ? "login-error login-demo-note" : "login-demo-note"}
                  onChange={(event) => { setPassword(event.target.value); setError(""); }} />
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
              {isPending ? "Đang kết nối..." : registering ? "Tạo tài khoản" : "Vào thế giới của bạn"}<ArrowRight aria-hidden="true" />
            </Button>
          </form>

          <p className="login-demo-note" id="login-demo-note"><ShieldCheck aria-hidden="true" />Đăng nhập bằng tài khoản VibeSocial của bạn.</p>
          <div className="login-small-note"><span />Ít lướt vô thức. Nhiều kết nối thật hơn.</div>
        </div>

        <footer className="login-footer"><span>© 2026 VibeSocial</span><span>Made for good company <Flower2 aria-hidden="true" /></span></footer>
      </section>
    </div>
  );
}
