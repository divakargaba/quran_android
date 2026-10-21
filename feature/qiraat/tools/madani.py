"""Madinah mushaf page data, read from MadaniDataSource.kt"""
import os, re
src=open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../pages/data/madani/src/main/kotlin/com/quran/labs/androidquran/pages/data/madani/MadaniDataSource.kt')).read()
def arr(name):
    m=re.search(name+r'\s*=\s*intArrayOf\((.*?)\)', src, re.S)
    body=re.sub(r'/\*.*?\*/','',m.group(1))
    return [int(x) for x in re.findall(r'-?\d+', body)]
SURA_FOR_PAGE=arr('suraForPageArray'); AYAH_FOR_PAGE=arr('ayahForPageArray'); AYAHS=arr('numberOfAyahsForSuraArray')
def page_ayahs(p):
    """list of (sura, ayah) on madani page p (1-based)"""
    s,a=SURA_FOR_PAGE[p-1],AYAH_FOR_PAGE[p-1]
    if p<604: es,ea=SURA_FOR_PAGE[p],AYAH_FOR_PAGE[p]
    else: es,ea=115,1
    out=[]
    while (s,a)!=(es,ea):
        out.append((s,a)); a+=1
        if a>AYAHS[s-1]: s+=1; a=1
    return out
