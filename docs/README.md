# Spectra Docs

The Spectra documentation site. It is intentionally boring to host: static files, no build step, no framework, no `node_modules`. Just HTML and two plain-text files for the robots.

```
docs/
├── index.html    # the human-facing site (self-contained: CSS inline, highlight.js from CDN)
├── llms.txt      # agentic index, per the llms.txt spec
├── AGENTS.md     # rules of the road for AI coding agents
└── vercel.json   # serves llms.txt / AGENTS.md with sensible content types
```

## Deploy to Vercel

No configuration framework, no surprises. Pick one:

**Dashboard:** Import the repo at vercel.com, set the **Root Directory** to `docs`, framework preset **Other**, and deploy. There's nothing to build.

**CLI:**

```bash
npm i -g vercel
cd docs
vercel        # preview
vercel --prod # production
```

After the first deploy, replace the `example.vercel.app` placeholders in `llms.txt` with your real domain so the agentic links resolve.

## Local preview

Any static server works:

```bash
cd docs
python3 -m http.server 8080
# open http://localhost:8080
```
