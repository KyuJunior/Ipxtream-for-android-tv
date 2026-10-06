# Custom Domain Setup Guide for IPXtream TV

This directory (`/docs`) is designed to host the official static web presence for IPXtream TV using GitHub Pages or any static web host.

## 1. GitHub Pages Configuration
1. In your GitHub repository, open **Settings** > **Pages**.
2. Under **Build and deployment**:
   - Source: **Deploy from a branch**
   - Branch: `main`
   - Folder: `/docs`
3. Click **Save**.

## 2. Custom Domain Routing
The `CNAME` file in this directory tells GitHub Pages which domain to answer for.

If your domain is `ipxtreamtv.com` (or your own custom domain name):
- Edit the `CNAME` file in `docs/CNAME` with your exact domain name (e.g. `yourdomain.com` or `tv.yourdomain.com`).

## 3. DNS Records Configuration
Configure the following DNS records at your DNS provider (Cloudflare, GoDaddy, Namecheap, etc.):

### For Apex / Root Domain (e.g. `yourdomain.com`)
Create 4 `A` records pointing to GitHub Pages IP addresses:
- `185.199.108.153`
- `185.199.109.153`
- `185.199.110.153`
- `185.199.111.153`

### For Subdomain (e.g. `tv.yourdomain.com`)
Create a `CNAME` record:
- Name: `tv`
- Target: `<your-github-username>.github.io`

## 4. HTTPS Enforce
Once DNS propagates (usually 5 to 30 minutes):
1. In GitHub repository **Settings** > **Pages**, verify your custom domain.
2. Check the box **Enforce HTTPS** to enable free automatic SSL/TLS certificates via Let's Encrypt.
