'use client';

import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import {
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  FileText,
  Calendar,
  Sparkles,
  Send,
  HelpCircle,
  RefreshCw,
  Scale,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  UserCheck,
  Receipt,
  AlertCircle
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import { Card, CardHeader, CardContent } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import {
  complianceApi,
  type ComplianceSummary,
  type ComplianceFinding,
  type ComplianceAiAnalysis,
  type ComplianceAiAnswer,
} from '@/lib/api/complianceApi';

const etb = (v?: number | null) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', maximumFractionDigits: 2 }).format(v || 0);

export default function ComplianceDashboardPage() {
  const [selectedPeriod, setSelectedPeriod] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [expandedFindingId, setExpandedFindingId] = useState<string | null>(null);

  // Q&A state
  const [questionInput, setQuestionInput] = useState<string>('');
  const [chatMessages, setChatMessages] = useState<
    Array<{ sender: 'user' | 'assistant'; text: string; provider?: string; evidence?: string[] }>
  >([]);

  // 1. Compliance Summary Query
  const summaryQuery = useQuery<ComplianceSummary>({
    queryKey: ['compliance-summary', selectedPeriod],
    queryFn: () => complianceApi.summary(selectedPeriod || undefined),
  });

  // 2. AI Analysis Mutation
  const aiAnalyzeMutation = useMutation<ComplianceAiAnalysis, Error, string | undefined>({
    mutationFn: (period?: string) => complianceApi.aiAnalyze(period),
  });

  // 3. Compliance Q&A Mutation
  const askMutation = useMutation<ComplianceAiAnswer, Error, { q: string; p?: string }>({
    mutationFn: ({ q, p }: { q: string; p?: string }) => complianceApi.ask(q, p),
    onSuccess: (data: ComplianceAiAnswer) => {
      setChatMessages((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: data.answer,
          provider: data.providerUsed,
          evidence: data.evidenceSources,
        },
      ]);
    },
    onError: (err: any) => {
      setChatMessages((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: `Error processing question: ${err?.message || 'Server error'}`,
        },
      ]);
    },
  });

  const handleSendQuestion = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const q = questionInput.trim();
    if (!q || askMutation.isPending) return;

    setChatMessages((prev) => [...prev, { sender: 'user', text: q }]);
    setQuestionInput('');
    askMutation.mutate({ q, p: selectedPeriod || undefined });
  };

  const handleSuggestedQuestion = (q: string) => {
    setChatMessages((prev) => [...prev, { sender: 'user', text: q }]);
    askMutation.mutate({ q, p: selectedPeriod || undefined });
  };

  const summary = summaryQuery.data;

  // Filter findings
  const filteredFindings = (summary?.findings || []).filter((f: ComplianceFinding) => {
    const matchesCat = categoryFilter === 'ALL' || f.category === categoryFilter;
    const matchesSev = severityFilter === 'ALL' || f.severity === severityFilter;
    return matchesCat && matchesSev;
  });

  const getSeverityBadge = (sev: string) => {
    switch (sev) {
      case 'CRITICAL':
        return <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-800 border border-red-200">Critical</span>;
      case 'HIGH':
        return <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">High</span>;
      case 'MEDIUM':
        return <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-yellow-100 text-yellow-800 border border-yellow-200">Medium</span>;
      default:
        return <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 border border-blue-200">Low</span>;
    }
  };

  const getStatusBadge = (status?: string, score?: number) => {
    if (status === 'COMPLIANT') {
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
          <ShieldCheck className="w-4 h-4 text-emerald-600" /> Compliant ({score}/100)
        </span>
      );
    }
    if (status === 'ACTION_REQUIRED') {
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold bg-amber-100 text-amber-800 border border-amber-300">
          <AlertTriangle className="w-4 h-4 text-amber-600" /> Action Required ({score}/100)
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold bg-red-100 text-red-800 border border-red-300">
        <ShieldAlert className="w-4 h-4 text-red-600" /> High Risk ({score || 0}/100)
      </span>
    );
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Page Header with Period Filter & Refresh */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <PageHeader
          title="AI Compliance & Regulatory Oversight"
          subtitle="Real-time compliance monitoring, statutory filing alerts, and AI-assisted regulatory audit for Ethiopian Tax & Labor laws."
        />
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 bg-white dark:bg-gray-800 border rounded-lg px-3 py-1.5 text-sm shadow-sm">
            <Calendar className="w-4 h-4 text-gray-500" />
            <input
              type="month"
              className="bg-transparent border-none text-sm focus:outline-none cursor-pointer"
              value={selectedPeriod}
              onChange={(e) => setSelectedPeriod(e.target.value)}
              placeholder="All Periods"
            />
            {selectedPeriod && (
              <button
                onClick={() => setSelectedPeriod('')}
                className="text-xs text-gray-400 hover:text-gray-600 ml-1"
              >
                Clear
              </button>
            )}
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => summaryQuery.refetch()}
            disabled={summaryQuery.isFetching}
          >
            <RefreshCw className={`w-4 h-4 mr-1.5 ${summaryQuery.isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Top Stat Cards */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card className="border-l-4 border-l-blue-600 shadow-sm">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">Overall Status</span>
              <Scale className="w-5 h-5 text-blue-600" />
            </div>
            <div className="mt-2">
              <div className="text-2xl font-bold text-gray-900 dark:text-white">
                {summary ? `${summary.complianceScore}%` : '---'}
              </div>
              <div className="mt-1">
                {getStatusBadge(summary?.overallStatus, summary?.complianceScore)}
              </div>
            </div>
          </CardContent>
        </Card>

        <StatCard
          title="Outstanding Tax Liability"
          value={etb(summary?.outstandingTaxLiability)}
          icon={<Receipt size={20} />}
          color="amber"
          changeLabel="Net Output VAT payable"
        />

        <StatCard
          title="Unpaid Payroll Liability"
          value={etb(summary?.unpaidPayrollLiability)}
          icon={<UserCheck size={20} />}
          color="purple"
          changeLabel="Draft/finalized awaiting payment"
        />

        <Card className="border-l-4 border-l-emerald-600 shadow-sm">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">Next Statutory Deadline</span>
              <Calendar className="w-5 h-5 text-emerald-600" />
            </div>
            <div className="mt-2">
              <div className="text-sm font-bold text-gray-900 dark:text-white">
                {summary?.nextDeadline || '30th of Month'}
              </div>
              <p className="mt-1 text-xs text-emerald-700 dark:text-emerald-400 font-medium">
                Monthly Ethiopian tax &amp; pension filing deadline
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* AI Compliance Intelligence Center */}
      <Card className="border-purple-200 dark:border-purple-900/40 bg-gradient-to-r from-purple-50/40 via-white to-blue-50/40 dark:from-gray-900 dark:via-gray-900 dark:to-gray-900">
        <CardHeader className="flex flex-row items-center justify-between border-b pb-4">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-purple-600 text-white rounded-lg shadow-sm">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-gray-900 dark:text-white">
                AI Regulatory &amp; Compliance Auditor
              </h2>
              <p className="text-xs text-gray-500">
                Grounded analysis of actual VAT filings, payroll withholdings, and Proclamation rules.
              </p>
            </div>
          </div>
          <Button
            size="sm"
            onClick={() => aiAnalyzeMutation.mutate(selectedPeriod || undefined)}
            loading={aiAnalyzeMutation.isPending}
            className="bg-purple-600 hover:bg-purple-700 text-white shadow-sm"
          >
            <Sparkles className="w-4 h-4 mr-1.5" />
            Run Comprehensive Audit
          </Button>
        </CardHeader>
        <CardContent className="pt-4 space-y-4">
          {aiAnalyzeMutation.data && (
            <div className="space-y-4 bg-white dark:bg-gray-800 rounded-xl p-5 border shadow-sm">
              <div className="flex items-center justify-between border-b pb-3">
                <div className="flex items-center gap-2">
                  <Badge variant={aiAnalyzeMutation.data.riskLevel === 'HIGH' ? 'danger' : 'warning'}>
                    Risk Assessment: {aiAnalyzeMutation.data.riskLevel}
                  </Badge>
                  <span className="text-xs text-gray-500">
                    Engine: <code className="bg-gray-100 dark:bg-gray-700 px-1.5 py-0.5 rounded">{aiAnalyzeMutation.data.providerUsed}</code>
                  </span>
                </div>
                <span className="text-xs text-gray-400">
                  {new Date(aiAnalyzeMutation.data.timestamp).toLocaleTimeString()}
                </span>
              </div>

              <div>
                <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
                  Executive Summary
                </h4>
                <p className="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">
                  {aiAnalyzeMutation.data.executiveSummary}
                </p>
              </div>

              {aiAnalyzeMutation.data.prioritizedActions && aiAnalyzeMutation.data.prioritizedActions.length > 0 && (
                <div>
                  <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                    Prioritized Action Roadmap
                  </h4>
                  <ul className="space-y-2">
                    {aiAnalyzeMutation.data.prioritizedActions.map((action: string, idx: number) => (
                      <li key={idx} className="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300">
                        <span className="flex-shrink-0 w-5 h-5 rounded-full bg-purple-100 text-purple-700 dark:bg-purple-900/60 dark:text-purple-300 flex items-center justify-center text-xs font-bold mt-0.5">
                          {idx + 1}
                        </span>
                        <span>{action}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Interactive Compliance Chat Box */}
          <div className="space-y-3 pt-2">
            <div className="text-xs font-medium text-gray-600 dark:text-gray-400 flex items-center gap-1.5">
              <HelpCircle className="w-3.5 h-3.5 text-blue-500" />
              <span>Ask the Compliance Assistant about Ethiopian Tax / Labor Law &amp; Internal Records:</span>
            </div>

            {/* Quick Suggested Prompts */}
            <div className="flex flex-wrap gap-2">
              {[
                'What is our outstanding VAT liability under Proclamation 1341/2024?',
                'Are all active employees enrolled in the verified payroll profile?',
                'Which tax rules are currently unverified and need accountant review?',
                'What is the employee and employer pension contribution rate?',
              ].map((suggestion, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSuggestedQuestion(suggestion)}
                  disabled={askMutation.isPending}
                  className="text-xs bg-gray-100 hover:bg-gray-200 dark:bg-gray-800 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 px-2.5 py-1 rounded-full transition-colors text-left"
                >
                  "{suggestion}"
                </button>
              ))}
            </div>

            {/* Chat Transcript */}
            {chatMessages.length > 0 && (
              <div className="max-h-64 overflow-y-auto space-y-3 p-3 bg-gray-50 dark:bg-gray-900/50 rounded-lg border text-sm">
                {chatMessages.map((msg, index) => (
                  <div
                    key={index}
                    className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}
                  >
                    <div
                      className={`max-w-[85%] rounded-lg p-3 ${
                        msg.sender === 'user'
                          ? 'bg-blue-600 text-white'
                          : 'bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 border shadow-xs'
                      }`}
                    >
                      <div className="whitespace-pre-wrap">{msg.text}</div>
                      {msg.provider && (
                        <div className="mt-1.5 pt-1.5 border-t border-gray-200 dark:border-gray-700 text-[10px] text-gray-400 flex items-center justify-between">
                          <span>Grounding: verified database records</span>
                          <span>Source: {msg.provider}</span>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
                {askMutation.isPending && (
                  <div className="text-xs text-gray-500 flex items-center gap-2">
                    <Sparkles className="w-3.5 h-3.5 animate-spin text-purple-600" />
                    <span>Analyzing compliance records and citing Ethiopian proclamations...</span>
                  </div>
                )}
              </div>
            )}

            {/* Question Input Form */}
            <form onSubmit={handleSendQuestion} className="flex gap-2">
              <input
                type="text"
                placeholder="Ask a question regarding VAT, progressive employment tax, pension, or overdue items..."
                value={questionInput}
                onChange={(e) => setQuestionInput(e.target.value)}
                disabled={askMutation.isPending}
                className="flex-1 text-sm bg-white dark:bg-gray-800 border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 shadow-sm"
              />
              <Button type="submit" loading={askMutation.isPending} disabled={!questionInput.trim()}>
                <Send className="w-4 h-4 mr-1" />
                Ask
              </Button>
            </form>
          </div>
        </CardContent>
      </Card>

      {/* Findings Table Section */}
      <Card>
        <CardHeader className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b pb-4">
          <div>
            <h2 className="text-base font-semibold text-gray-900 dark:text-white flex items-center gap-2">
              <AlertCircle className="w-5 h-5 text-amber-500" />
              Compliance Findings &amp; Audit Trail ({filteredFindings.length})
            </h2>
            <p className="text-xs text-gray-500">
              Deterministic anomalies detected across VAT reconciliation, payroll finalization, and statutory rules.
            </p>
          </div>

          {/* Filters */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1 bg-gray-100 dark:bg-gray-800 p-1 rounded-lg text-xs font-medium">
              <span className="px-1 text-gray-400">Category:</span>
              {['ALL', 'TAX', 'PAYROLL', 'DEADLINE', 'RECONCILIATION'].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setCategoryFilter(cat)}
                  className={`px-2 py-0.5 rounded transition-colors ${
                    categoryFilter === cat
                      ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-400 shadow-xs font-semibold'
                      : 'text-gray-600 dark:text-gray-400 hover:text-gray-900'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-1 bg-gray-100 dark:bg-gray-800 p-1 rounded-lg text-xs font-medium">
              <span className="px-1 text-gray-400">Severity:</span>
              {['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((sev) => (
                <button
                  key={sev}
                  onClick={() => setSeverityFilter(sev)}
                  className={`px-2 py-0.5 rounded transition-colors ${
                    severityFilter === sev
                      ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-400 shadow-xs font-semibold'
                      : 'text-gray-600 dark:text-gray-400 hover:text-gray-900'
                  }`}
                >
                  {sev}
                </button>
              ))}
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-0">
          {summaryQuery.isLoading ? (
            <div className="p-8 text-center text-sm text-gray-500">
              Scanning database records and evaluating compliance rules...
            </div>
          ) : filteredFindings.length === 0 ? (
            <div className="p-8 text-center">
              <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto mb-2" />
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                No Compliance Findings Matching Filters
              </h3>
              <p className="text-xs text-gray-500 mt-1">
                All inspected records and statutory conditions satisfy the current criteria.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-gray-100 dark:divide-gray-800">
              {filteredFindings.map((finding: ComplianceFinding) => {
                const isExpanded = expandedFindingId === finding.id;
                return (
                  <div key={finding.id} className="p-4 hover:bg-gray-50/70 dark:hover:bg-gray-800/40 transition-colors">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-1.5 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          {getSeverityBadge(finding.severity)}
                          <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                            {finding.category}
                          </span>
                          <span className="text-sm font-medium text-gray-900 dark:text-white">
                            {finding.title}
                          </span>
                          {finding.humanApprovalRequired && (
                            <span className="text-[11px] bg-purple-50 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300 px-2 py-0.5 rounded border border-purple-200 dark:border-purple-800 font-medium">
                              Human Approval Required
                            </span>
                          )}
                        </div>

                        <p className="text-xs text-gray-600 dark:text-gray-300">
                          {finding.explanation}
                        </p>

                        <div className="flex flex-wrap items-center gap-4 text-xs text-gray-500 pt-1">
                          {finding.amount !== undefined && finding.amount !== null && finding.amount > 0 && (
                            <span className="font-semibold text-gray-900 dark:text-white">
                              Exposure / Amount: {etb(finding.amount)}
                            </span>
                          )}
                          <span>Period: {finding.affectedPeriod || 'All Historical'}</span>
                        </div>
                      </div>

                      <button
                        onClick={() => setExpandedFindingId(isExpanded ? null : finding.id)}
                        className="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700"
                        title={isExpanded ? 'Collapse Details' : 'Expand Details'}
                      >
                        {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                      </button>
                    </div>

                    {/* Expanded Action & Audit Details */}
                    {isExpanded && (
                      <div className="mt-4 pt-3 border-t border-dashed space-y-3 bg-gray-50/50 dark:bg-gray-800/20 p-3 rounded-lg text-xs">
                        {finding.recommendedAction && (
                          <div>
                            <span className="font-semibold text-gray-700 dark:text-gray-300 block mb-0.5">
                              Recommended Corrective Action:
                            </span>
                            <p className="text-gray-800 dark:text-gray-200 bg-white dark:bg-gray-800 p-2 rounded border">
                              {finding.recommendedAction}
                            </p>
                          </div>
                        )}

                        {finding.sourceRecords && (
                          <div>
                            <span className="font-semibold text-gray-700 dark:text-gray-300 block mb-1">
                              Contributing Source Records:
                            </span>
                            <div className="bg-white dark:bg-gray-800 px-2 py-1 rounded border text-[11px] text-gray-700 dark:text-gray-300 font-mono">
                              {finding.sourceRecords}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
