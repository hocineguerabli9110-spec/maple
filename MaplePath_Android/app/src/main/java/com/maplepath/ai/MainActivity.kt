package com.maplepath.ai

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

class MainActivity : Activity() {
 private lateinit var body: LinearLayout
 private val prefs by lazy { getSharedPreferences("maplepath_local", MODE_PRIVATE) }
 private var candidates = JSONArray()
 private var jobs = JSONArray()
 private var lastReport = ""
 private val pickCv = 101
 private val pickCsv = 102
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); PDFBoxResourceLoader.init(applicationContext)
  candidates = JSONArray(prefs.getString("candidates", "[]")); jobs = JSONArray(prefs.getString("jobs", "[]")); home() }
 private fun persist() { prefs.edit().putString("candidates", candidates.toString()).putString("jobs", jobs.toString()).apply() }
 private fun label(s:String, size:Float=16f):TextView = TextView(this).apply { text=s; textSize=size; setTextColor(Color.rgb(22,40,57)); setPadding(0,10,0,10) }
 private fun button(s:String, f:()->Unit) { body.addView(Button(this).apply { text=s; setOnClickListener { f() } }) }
 private fun field(hint:String):EditText = EditText(this).apply { this.hint=hint; setSingleLine(true); body.addView(this) }
 private fun page(title:String) { val scroll=ScrollView(this); body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(30,38,30,36) }; scroll.addView(body); setContentView(scroll); body.addView(label("🍁 MaplePath AI",25f)); body.addView(label(title,21f)) }
 private fun home() { page("لوحة التحكم · Offline MVP"); body.addView(label("المرشحون: ${candidates.length()}   |   الوظائف: ${jobs.length()}"));
  button("إضافة ملفات CV (PDF / DOCX / TXT)") { openFile("*/*", pickCv, true) }
  button("إضافة مرشح يدويًا") { manualCandidate() }
  button("استيراد وظائف CSV") { openFile("text/*", pickCsv, false) }
  button("إضافة وظيفة يدويًا") { manualJob() }
  button("تحميل 3 وظائف تجريبية") { val data=assets.open("demo_jobs.csv").bufferedReader().readText(); importCsv(data); home() }
  button("المرشحون والمطابقة") { showCandidates() }
  button("الوظائف المحفوظة") { showJobs() }
  body.addView(label("لا يوجد بحث إنترنت في هذا الإصدار. بيانات الوظائف التجريبية غير حقيقية. لا تُدخل بيانات عملاء حساسة قبل إضافة حماية مناسبة.",13f)) }
 private fun openFile(type:String, request:Int, multi:Boolean) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type=type; if (multi) putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true) },request) }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null)return
  try { if(requestCode==pickCv) { val uris=mutableListOf<Uri>(); data.clipData?.let { for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri) }; if(uris.isEmpty())data.data?.let{uris.add(it)}; var n=0
    uris.forEach { uri -> val name=contentResolver.query(uri,null,null,null,null)?.use { c -> val idx=c.getColumnIndex("_display_name"); if(c.moveToFirst()&&idx>=0)c.getString(idx) else "CV" } ?: "CV"; val bytes=contentResolver.openInputStream(uri)!!.use { it.readBytes() }; val content=extract(name,bytes).take(100000); if(content.isNotBlank()) { candidates.put(JSONObject().put("name",name.substringBeforeLast('.')).put("cv",content));n++ } }; persist(); Toast.makeText(this,"تم استيراد $n CV",Toast.LENGTH_LONG).show(); home()
   } else if(requestCode==pickCsv) { val csv=contentResolver.openInputStream(data.data!!)!!.bufferedReader().use{it.readText()}; importCsv(csv); home() }
  } catch(e:Exception) { Toast.makeText(this,"خطأ: ${e.message}",Toast.LENGTH_LONG).show() } }
 private fun extract(name:String,bytes:ByteArray):String { val lower=name.lowercase(); return when { lower.endsWith(".pdf") -> PDDocument.load(bytes).use{PDFTextStripper().getText(it)}
   lower.endsWith(".docx") -> { val out=StringBuilder(); ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> var entry=zip.nextEntry; while(entry!=null){ if(entry.name=="word/document.xml") { val xml=zip.readBytes().toString(Charsets.UTF_8); val doc=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(InputSource(StringReader(xml))); val nodes=doc.getElementsByTagNameNS("*","t"); for(i in 0 until nodes.length)out.append(nodes.item(i).textContent).append(' ') }; entry=zip.nextEntry } }; out.toString() }
   else -> bytes.toString(Charsets.UTF_8) } }
 private fun manualCandidate() { page("إضافة مرشح"); val name=field("اسم المرشح"); val cv=field("مهارات وخبرة ولغات المرشح"); button("حفظ") { if(name.text.isNotBlank()) { candidates.put(JSONObject().put("name",name.text.toString()).put("cv",cv.text.toString())); persist();home() } }; button("رجوع") {home()} }
 private fun manualJob() { page("إضافة وظيفة"); val title=field("عنوان الوظيفة"); val company=field("الشركة"); val province=field("المقاطعة"); val desc=field("المهارات والمتطلبات"); val evidence=field("دليل قبول مرشحين دوليين (إن وجد)"); button("حفظ") { if(title.text.isNotBlank()) { jobs.put(JSONObject().put("title",title.text.toString()).put("company",company.text.toString()).put("province",province.text.toString()).put("description",desc.text.toString()).put("evidence",evidence.text.toString()).put("international_status", "unknown"));persist();home() } }; button("رجوع") {home()} }
 private fun parseCsv(input:String):List<List<String>> { val rows=mutableListOf<List<String>>(); val row=mutableListOf<String>(); val cell=StringBuilder(); var quoted=false; var i=0
  while(i<input.length){val c=input[i]; when { c=='"' -> { if(quoted&&i+1<input.length&&input[i+1]=='"'){cell.append('"');i++}else quoted=!quoted }; c==','&&!quoted -> {row.add(cell.toString());cell.clear()}; (c=='\n'||c=='\r')&&!quoted -> { if(c=='\r'&&i+1<input.length&&input[i+1]=='\n')i++;row.add(cell.toString());cell.clear(); if(row.any{it.isNotBlank()})rows.add(row.toList());row.clear() }; else -> cell.append(c) };i++ }; row.add(cell.toString());if(row.any{it.isNotBlank()})rows.add(row);return rows }
 private fun importCsv(csv:String) { val rows=parseCsv(csv);if(rows.isEmpty())return; val headers=rows[0].map{it.trim().lowercase().removePrefix("\ufeff")}; val required=listOf("title","company","province","description");if(!headers.containsAll(required)){Toast.makeText(this,"CSV يحتاج title, company, province, description",Toast.LENGTH_LONG).show();return}; var count=0
  rows.drop(1).forEach{cols-> val obj=JSONObject();headers.forEachIndexed{idx,key->obj.put(key,cols.getOrElse(idx){""})}; if(obj.optString("title").isNotBlank()){jobs.put(obj);count++} };persist();Toast.makeText(this,"تم استيراد $count وظيفة",Toast.LENGTH_LONG).show() }
 private fun showCandidates() {page("المرشحون");for(i in 0 until candidates.length()){val idx=i;val c=candidates.getJSONObject(i);button("${i+1}. ${c.optString("name")}"){matches(idx)} };button("رجوع") {home()} }
 private fun showJobs(){page("الوظائف");for(i in 0 until jobs.length()){val j=jobs.getJSONObject(i);body.addView(label("${j.optString("title")} · ${j.optString("company")} · ${j.optString("province")}",15f))};button("رجوع"){home()} }
 private fun tokens(s:String):Set<String> = Regex("[\\p{L}\\p{N}]{3,}").findAll(s.lowercase()).map{it.value}.filterNot{it in setOf("the","and","for","with","des","les","dans","pour","une","sur","avec")}.toSet()
 private fun matches(index:Int) { val c=candidates.getJSONObject(index);page("مطابقة: ${c.optString("name")}");val cv=tokens(c.optString("cv")); val scored=(0 until jobs.length()).map{jobs.getJSONObject(it)}.map{j-> val target=tokens(j.optString("title")+" "+j.optString("description"));val hits=target.intersect(cv); val score=if(target.isEmpty())0 else (hits.size.toDouble()/target.size*100).toInt().coerceAtMost(100);Triple(j,score,hits)}.sortedByDescending{it.second}
  val report=StringBuilder("MaplePath AI — DEMO MATCH REPORT\nCandidate: ${c.optString("name")}\nScores are keyword overlap, not employment probability.\n\n")
  scored.forEach{(j,score,hits)->val title=j.optString("title"); val company=j.optString("company");val province=j.optString("province"); val intl=j.optString("international_status","unknown");val evidence=j.optString("evidence");body.addView(label("$title · $company ($province)\nتوافق الكلمات: $score% | قبول دولي: $intl\nالدليل: ${evidence.ifBlank{"غير متوفر"}}",15f));report.append("$title | $company | $province | $score% | international=$intl | evidence=$evidence | matched=${hits.joinToString()}\n") }
  lastReport=report.toString();button("مشاركة التقرير") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,lastReport)},"مشاركة التقرير")) };button("رجوع"){showCandidates()} }
}
