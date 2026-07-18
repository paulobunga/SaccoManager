import re
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    HRFlowable, PageBreak
)
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.platypus import BaseDocTemplate, PageTemplate, Frame
from reportlab.platypus.flowables import Flowable

# ── Colours ──────────────────────────────────────────────────────────────────
DARK_GREEN   = colors.HexColor("#1a3c2e")
MID_GREEN    = colors.HexColor("#2e7d52")
LIGHT_GREEN  = colors.HexColor("#c8e6c9")
ROW_ALT      = colors.HexColor("#f4faf5")
WHITE        = colors.white
BLACK        = colors.HexColor("#1a1a1a")
GREY         = colors.HexColor("#888888")
HEADER_BG    = DARK_GREEN

PAGE_W, PAGE_H = A4

# ── Footer ────────────────────────────────────────────────────────────────────
def add_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 7.5)
    canvas.setFillColor(GREY)
    text = f"SACCO Manager — Software Development Proposal  |  Page {doc.page}"
    canvas.drawCentredString(PAGE_W / 2, 1.1 * cm, text)
    canvas.setStrokeColor(LIGHT_GREEN)
    canvas.setLineWidth(0.5)
    canvas.line(2 * cm, 1.4 * cm, PAGE_W - 2 * cm, 1.4 * cm)
    canvas.restoreState()

# ── Styles ────────────────────────────────────────────────────────────────────
base = getSampleStyleSheet()

def style(name, parent="Normal", **kw):
    return ParagraphStyle(name, parent=base[parent], **kw)

S = {
    "h1": style("H1", "Heading1",
                fontSize=20, textColor=DARK_GREEN, spaceAfter=6,
                fontName="Helvetica-Bold", leading=26),
    "h2": style("H2", "Heading2",
                fontSize=13, textColor=DARK_GREEN, spaceBefore=18,
                spaceAfter=4, fontName="Helvetica-Bold", leading=18),
    "h3": style("H3", "Heading3",
                fontSize=11, textColor=MID_GREEN, spaceBefore=14,
                spaceAfter=3, fontName="Helvetica-Bold", leading=15),
    "h4": style("H4", "Normal",
                fontSize=10, textColor=BLACK, spaceBefore=10,
                spaceAfter=2, fontName="Helvetica-Bold", leading=14),
    "body": style("Body", "Normal",
                  fontSize=10, textColor=BLACK, leading=15, spaceAfter=5),
    "bullet": style("Bullet", "Normal",
                    fontSize=10, textColor=BLACK, leading=14,
                    leftIndent=16, bulletIndent=4, spaceAfter=3),
    "bold_line": style("BoldLine", "Normal",
                       fontSize=10, textColor=BLACK, leading=14,
                       fontName="Helvetica-Bold", spaceAfter=3),
    "footer_note": style("FootNote", "Normal",
                         fontSize=8.5, textColor=GREY, leading=12,
                         spaceAfter=4, fontName="Helvetica-Oblique"),
    "cell_header": style("CellHdr", "Normal",
                         fontSize=9, textColor=WHITE,
                         fontName="Helvetica-Bold", leading=12),
    "cell": style("Cell", "Normal",
                  fontSize=9, textColor=BLACK, leading=12),
    "cell_center": style("CellC", "Normal",
                         fontSize=9, textColor=BLACK,
                         leading=12, alignment=TA_CENTER),
    "meta": style("Meta", "Normal",
                  fontSize=9.5, textColor=GREY,
                  leading=13, spaceAfter=3),
    "tier_price": style("TierPrice", "Normal",
                        fontSize=13, textColor=DARK_GREEN,
                        fontName="Helvetica-Bold", leading=18, spaceAfter=2),
}

# ── Markdown parser → ReportLab flowables ────────────────────────────────────
def parse_md(path):
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()

    flowables = []
    i = 0

    def hr():
        return HRFlowable(width="100%", thickness=1, color=LIGHT_GREEN,
                          spaceAfter=8, spaceBefore=8)

    # Collect a table block starting at line i (i points to header row)
    def consume_table(i):
        rows_raw = []
        while i < len(lines):
            line = lines[i].strip()
            if not line.startswith("|"):
                break
            # skip separator rows like |---|:---:|
            if re.match(r"^\|[-:| ]+\|$", line):
                i += 1
                continue
            cells = [c.strip() for c in line.strip("|").split("|")]
            rows_raw.append(cells)
            i += 1
        return rows_raw, i

    while i < len(lines):
        raw = lines[i]
        line = raw.strip()

        # blank
        if not line:
            flowables.append(Spacer(1, 4))
            i += 1
            continue

        # h1
        if line.startswith("# ") and not line.startswith("## "):
            flowables.append(Paragraph(line[2:].strip(), S["h1"]))
            flowables.append(HRFlowable(width="100%", thickness=2.5,
                                        color=DARK_GREEN, spaceAfter=8))
            i += 1
            continue

        # h2
        if line.startswith("## ") and not line.startswith("### "):
            flowables.append(Paragraph(line[3:].strip(), S["h2"]))
            flowables.append(HRFlowable(width="100%", thickness=1,
                                        color=LIGHT_GREEN, spaceAfter=4))
            i += 1
            continue

        # h3
        if line.startswith("### ") and not line.startswith("#### "):
            text = line[4:].strip()
            # Detect tier heading — contains "TIER" and "UGX"
            if "TIER" in text.upper() and "UGX" in text:
                flowables.append(Spacer(1, 6))
                flowables.append(Paragraph(text, S["h3"]))
            else:
                flowables.append(Paragraph(text, S["h3"]))
            i += 1
            continue

        # h4
        if line.startswith("#### "):
            flowables.append(Paragraph(line[5:].strip(), S["h4"]))
            i += 1
            continue

        # hr
        if line.startswith("---"):
            flowables.append(hr())
            i += 1
            continue

        # table
        if line.startswith("|"):
            rows_raw, i = consume_table(i)
            if not rows_raw:
                continue

            col_count = len(rows_raw[0])
            col_w_first = 5.5 * cm
            col_w_rest = (PAGE_W - 4*cm - col_w_first) / max(col_count - 1, 1)
            col_widths = [col_w_first] + [col_w_rest] * (col_count - 1)

            tdata = []
            for r_idx, row in enumerate(rows_raw):
                cells = []
                for c_idx, cell in enumerate(row):
                    # strip bold markers
                    cell = re.sub(r"\*\*(.+?)\*\*", r"\1", cell)
                    st = S["cell_header"] if r_idx == 0 else (
                        S["cell"] if c_idx == 0 else S["cell_center"])
                    cells.append(Paragraph(cell, st))
                tdata.append(cells)

            ts = TableStyle([
                ("BACKGROUND",  (0, 0), (-1, 0), HEADER_BG),
                ("TEXTCOLOR",   (0, 0), (-1, 0), WHITE),
                ("FONTNAME",    (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID",        (0, 0), (-1, -1), 0.4, LIGHT_GREEN),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [WHITE, ROW_ALT]),
                ("VALIGN",      (0, 0), (-1, -1), "MIDDLE"),
                ("TOPPADDING",  (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING",(0,0), (-1, -1), 5),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING",(0, 0), (-1, -1), 6),
            ])
            t = Table(tdata, colWidths=col_widths, repeatRows=1)
            t.setStyle(ts)
            flowables.append(Spacer(1, 4))
            flowables.append(t)
            flowables.append(Spacer(1, 8))
            continue

        # bullet
        if line.startswith("- ") or line.startswith("* "):
            text = line[2:].strip()
            text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
            text = re.sub(r"\*(.+?)\*",   r"<i>\1</i>", text)
            flowables.append(Paragraph(f"• &nbsp; {text}", S["bullet"]))
            i += 1
            continue

        # checkbox lines (acceptance section)
        if line.startswith("☐"):
            text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", line)
            flowables.append(Paragraph(text, S["body"]))
            i += 1
            continue

        # bold-only line (e.g. **Investment: UGX 2,100,000**)
        bold_match = re.match(r"^\*\*(.+?)\*\*$", line)
        if bold_match:
            inner = bold_match.group(1)
            if "UGX" in inner and ("Investment" in inner or "Timeline" in inner):
                flowables.append(Paragraph(inner, S["tier_price"]))
            else:
                flowables.append(Paragraph(f"<b>{inner}</b>", S["body"]))
            i += 1
            continue

        # normal paragraph
        text = line
        text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
        text = re.sub(r"\*(.+?)\*",   r"<i>\1</i>", text)
        # meta lines at top of doc (Prepared by, Date, Reference)
        if text.startswith("<b>Prepared") or text.startswith("<b>Date") or \
           text.startswith("<b>Reference") or text.startswith("<b>Prepared for"):
            flowables.append(Paragraph(text, S["meta"]))
        else:
            flowables.append(Paragraph(text, S["body"]))
        i += 1

    return flowables

# ── Build PDF ─────────────────────────────────────────────────────────────────
output = "SACCO_Manager_Proposal.pdf"

doc = SimpleDocTemplate(
    output,
    pagesize=A4,
    leftMargin=2*cm, rightMargin=2*cm,
    topMargin=2*cm,  bottomMargin=2*cm,
    title="SACCO Manager — Software Development Proposal",
    author="Development Team",
)

story = parse_md("SACCO_Manager_Proposal.md")

doc.build(story, onFirstPage=add_footer, onLaterPages=add_footer)
print(f"✅  PDF saved: {output}")
