'use client';
import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Package, Lock, User, Mail, AlertCircle, ArrowRight, CheckCircle2, Phone } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

const schema = z.object({
  fullName: z.string().min(2, 'Full name must be at least 2 characters'),
  email: z.string().email('Please enter a valid email address'),
  phone: z.string().min(8, 'Phone number must be at least 8 digits'),
  username: z.string().min(3, 'Username must be at least 3 characters').regex(/^[a-z0-9_]+$/, 'Only lowercase letters, numbers and underscores'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  confirmPassword: z.string(),
  agreeToTerms: z.boolean().refine(v => v === true, 'You must accept the terms'),
}).refine(d => d.password === d.confirmPassword, {
  message: 'Passwords do not match',
  path: ['confirmPassword'],
});

type FormData = z.infer<typeof schema>;

const PasswordStrength = ({ password }: { password: string }) => {
  const checks = [
    { label: '8+ characters', ok: password.length >= 8 },
    { label: 'Uppercase letter', ok: /[A-Z]/.test(password) },
    { label: 'Number', ok: /[0-9]/.test(password) },
    { label: 'Special character', ok: /[^a-zA-Z0-9]/.test(password) },
  ];
  const score = checks.filter(c => c.ok).length;
  const colors = ['', 'bg-red-400', 'bg-orange-400', 'bg-yellow-400', 'bg-emerald-500'];
  const labels = ['', 'Weak', 'Fair', 'Good', 'Strong'];

  if (!password) return null;
  return (
    <div className="mt-2 space-y-2">
      <div className="flex gap-1">
        {[1,2,3,4].map(i => (
          <div key={i} className={`h-1.5 flex-1 rounded-full transition-colors ${i <= score ? colors[score] : 'bg-gray-200 dark:bg-gray-600'}`} />
        ))}
      </div>
      <div className="flex items-center justify-between">
        <div className="flex gap-3 flex-wrap">
          {checks.map(c => (
            <span key={c.label} className={`flex items-center gap-1 text-xs ${c.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-400'}`}>
              <CheckCircle2 size={11} className={c.ok ? 'opacity-100' : 'opacity-30'} />
              {c.label}
            </span>
          ))}
        </div>
        {score > 0 && <span className={`text-xs font-medium ${score >= 3 ? 'text-emerald-600 dark:text-emerald-400' : 'text-orange-500'}`}>{labels[score]}</span>}
      </div>
    </div>
  );
};

export default function SignupPage() {
  const router = useRouter();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [success, setSuccess] = useState(false);
  const [serverError, setServerError] = useState('');

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema), defaultValues: { agreeToTerms: false } });

  const password = watch('password', '');

  const onSubmit = async (data: FormData) => {
    setServerError('');
    // Simulate API call
    await new Promise(r => setTimeout(r, 1000));
    // For demo: show success then redirect to login
    setSuccess(true);
    setTimeout(() => router.push('/login'), 2000);
  };

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-6">
        <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-10 text-center max-w-sm w-full">
          <div className="w-16 h-16 bg-emerald-100 dark:bg-emerald-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle2 className="text-emerald-600 dark:text-emerald-400" size={34} />
          </div>
          <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-2">Account Created!</h2>
          <p className="text-gray-500 dark:text-gray-400 text-sm mb-6">Your account has been created successfully. Redirecting you to login…</p>
          <div className="w-full bg-gray-100 dark:bg-gray-700 rounded-full h-1.5">
            <div className="bg-emerald-500 h-1.5 rounded-full animate-[width_2s_linear]" style={{ width: '100%', transition: 'width 2s linear' }} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex">
      {/* Left panel – brand */}
      <div className="hidden lg:flex lg:w-5/12 bg-gradient-to-br from-blue-600 via-blue-700 to-indigo-800 flex-col items-center justify-center p-12 relative overflow-hidden">
        <div className="absolute top-0 left-0 w-72 h-72 bg-white/5 rounded-full -translate-x-1/2 -translate-y-1/2" />
        <div className="absolute bottom-0 right-0 w-96 h-96 bg-white/5 rounded-full translate-x-1/3 translate-y-1/3" />

        <div className="relative z-10 text-center max-w-xs">
          <div className="flex items-center justify-center gap-3 mb-8">
            <div className="w-14 h-14 bg-white/20 backdrop-blur-sm rounded-2xl flex items-center justify-center">
              <Package className="text-white" size={30} />
            </div>
            <span className="text-white text-3xl font-bold">StockFlow</span>
          </div>

          <h2 className="text-white text-2xl font-bold mb-4">Start managing your inventory today</h2>
          <p className="text-blue-100 text-sm leading-relaxed mb-8">
            Join thousands of businesses using StockFlow to control their inventory, track sales, and grow their profits.
          </p>

          {/* Feature list */}
          {[
            'Real-time stock tracking',
            'Multi-warehouse management',
            'Sales & purchase reports',
            'Role-based access control',
            'Full audit trail',
          ].map(feature => (
            <div key={feature} className="flex items-center gap-3 mb-3 text-left">
              <div className="w-5 h-5 bg-white/20 rounded-full flex items-center justify-center shrink-0">
                <CheckCircle2 size={12} className="text-white" />
              </div>
              <span className="text-blue-100 text-sm">{feature}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Right panel – form */}
      <div className="flex-1 flex items-center justify-center p-6 bg-gray-50 dark:bg-gray-900 overflow-y-auto">
        <div className="w-full max-w-lg py-8">
          {/* Mobile logo */}
          <div className="flex items-center justify-center gap-2 mb-6 lg:hidden">
            <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center">
              <Package className="text-white" size={22} />
            </div>
            <span className="text-gray-900 dark:text-white text-2xl font-bold">StockFlow</span>
          </div>

          <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-xl p-8">
            <div className="mb-7">
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-1">Create your account</h1>
              <p className="text-gray-500 dark:text-gray-400 text-sm">Fill in the details below to get started</p>
            </div>

            {serverError && (
              <div className="mb-5 flex items-start gap-3 p-3.5 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
                <AlertCircle size={18} className="text-red-600 shrink-0 mt-0.5" />
                <p className="text-red-700 dark:text-red-400 text-sm">{serverError}</p>
              </div>
            )}

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              {/* Full Name */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Full Name</label>
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400"><User size={16} /></span>
                  <input
                    type="text"
                    placeholder="e.g. Tekle Haile"
                    {...register('fullName')}
                    className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${errors.fullName ? 'border-red-400' : 'border-gray-200 dark:border-gray-600'}`}
                  />
                </div>
                {errors.fullName && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.fullName.message}</p>}
              </div>

              {/* Email + Phone */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Email Address</label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400"><Mail size={16} /></span>
                    <input
                      type="email"
                      placeholder="you@company.com"
                      {...register('email')}
                      className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${errors.email ? 'border-red-400' : 'border-gray-200 dark:border-gray-600'}`}
                    />
                  </div>
                  {errors.email && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.email.message}</p>}
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Phone Number</label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400"><Phone size={16} /></span>
                    <input
                      type="tel"
                      placeholder="+251 91 234 5678"
                      {...register('phone')}
                      className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${errors.phone ? 'border-red-400' : 'border-gray-200 dark:border-gray-600'}`}
                    />
                  </div>
                  {errors.phone && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.phone.message}</p>}
                </div>
              </div>

              {/* Username */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Username</label>
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400 font-mono text-sm">@</span>
                  <input
                    type="text"
                    placeholder="your_username"
                    {...register('username')}
                    className={`w-full pl-10 pr-4 py-2.5 rounded-xl border text-sm bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${errors.username ? 'border-red-400' : 'border-gray-200 dark:border-gray-600'}`}
                  />
                </div>
                {errors.username && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.username.message}</p>}
              </div>

              {/* Password */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Password</label>
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400"><Lock size={16} /></span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Create a strong password"
                    {...register('password')}
                    className={`w-full pl-10 pr-11 py-2.5 rounded-xl border text-sm bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${errors.password ? 'border-red-400' : 'border-gray-200 dark:border-gray-600'}`}
                  />
                  <button type="button" onClick={() => setShowPassword(v => !v)} className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                <PasswordStrength password={password} />
                {errors.password && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.password.message}</p>}
              </div>

              {/* Confirm Password */}
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">Confirm Password</label>
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400"><Lock size={16} /></span>
                  <input
                    type={showConfirm ? 'text' : 'password'}
                    placeholder="Repeat your password"
                    {...register('confirmPassword')}
                    className={`w-full pl-10 pr-11 py-2.5 rounded-xl border text-sm bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${errors.confirmPassword ? 'border-red-400' : 'border-gray-200 dark:border-gray-600'}`}
                  />
                  <button type="button" onClick={() => setShowConfirm(v => !v)} className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                    {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {errors.confirmPassword && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{errors.confirmPassword.message}</p>}
              </div>

              {/* Terms checkbox */}
              <div className="flex items-start gap-3">
                <input
                  type="checkbox"
                  id="terms"
                  {...register('agreeToTerms')}
                  className="mt-0.5 w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <label htmlFor="terms" className="text-sm text-gray-600 dark:text-gray-400">
                  I agree to the{' '}
                  <Link href="/terms" className="text-blue-600 hover:underline font-medium">Terms of Service</Link>{' '}
                  and{' '}
                  <Link href="/privacy" className="text-blue-600 hover:underline font-medium">Privacy Policy</Link>
                </label>
              </div>
              {errors.agreeToTerms && <p className="text-xs text-red-600 dark:text-red-400">{errors.agreeToTerms.message}</p>}

              {/* Submit */}
              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed text-white font-semibold rounded-xl transition-colors text-sm shadow-sm"
              >
                {isSubmitting ? (
                  <>
                    <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                    </svg>
                    Creating account…
                  </>
                ) : (
                  <>Create Account <ArrowRight size={16} /></>
                )}
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
              Already have an account?{' '}
              <Link href="/login" className="text-blue-600 hover:text-blue-700 dark:text-blue-400 font-semibold">
                Sign in
              </Link>
            </p>
          </div>
          <p className="mt-6 text-center text-xs text-gray-400 dark:text-gray-600">
            © {new Date().getFullYear()} StockFlow. All rights reserved.
          </p>
        </div>
      </div>
    </div>
  );
}
