package com.gk.octaveanalyzer;
import java.util.Locale;

/** Indicative FFT octave-band estimator; not a certified class 1/2 filter. */
public final class OctaveAnalyzer {
 public static final int N=16384;
 public static final double FLOOR=-150;
 public static final String[] OCT={"31.5","63","125","250","500","1k","2k","4k","8k","16k"};
 public static final String[] THIRD={"31.5","40","50","63","80","100","125","160","200","250","315","400","500","630","800","1k","1.25k","1.6k","2k","2.5k","3.15k","4k","5k","6.3k","8k","10k","12.5k","16k"};
 public static final class Result {
  public final String[] names; public final double[] center,live;
  public final double overall,oneK,overallZ; public final boolean clip;
  Result(String[] n,double[] c,double[] x,double o,double k,double z,boolean cl){names=n;center=c;live=x;overall=o;oneK=k;overallZ=z;clip=cl;}
 }
 public static double db(double power){return 10*Math.log10(Math.max(1e-15,power));}
 public static double weight(double f,int type) {
  if(type==0)return 0;
  double f2=f*f, r122=12200.0*12200, r206=20.6*20.6;
  double c=(r122*f2)/((f2+r206)*(f2+r122));
  if(type==2)return 20*Math.log10(Math.max(1e-30,c))+0.06;
  double a=r122*f2*f2/((f2+r206)*(f2+r122)*Math.sqrt((f2+107.7*107.7)*(f2+737.9*737.9)));
  return 20*Math.log10(Math.max(1e-30,a))+2.0;
 }
 public static Result analyze(short[] s,int sr,int bpo,int weighting) {
  if(s.length!=N)throw new IllegalArgumentException("Expected 16384 PCM samples");
  String[] names=bpo==1?OCT:THIRD;
  double[] c=new double[names.length],lo=new double[names.length],hi=new double[names.length],powers=new double[names.length];
  double h=Math.pow(10,3.0/(20*bpo));
  int k0=bpo==1?-5:-15;
  for(int j=0;j<c.length;j++){c[j]=1000*Math.pow(10,3.0*(k0+j)/(10*bpo));lo[j]=c[j]/h;hi[j]=c[j]*h;}
  double[] re=new double[N],im=new double[N];
  boolean clip=false; double avg=0,ws=0;
  for(short x:s){avg+=x/32768.0;if(x>=32760||x<=-32760)clip=true;}
  avg/=N;
  for(int i=0;i<N;i++){double w=0.5*(1-Math.cos(2*Math.PI*i/(N-1)));re[i]=(s[i]/32768.0-avg)*w;ws+=w*w;}
  fft(re,im);
  double total=0,oneK=0,totalZ=0;
  double kLo=1000/Math.pow(10,3.0/60),kHi=1000*Math.pow(10,3.0/60);
  for(int bin=1;bin<N/2;bin++){
   double f=(double)bin*sr/N;
   if(f>Math.min(sr/2.0,20000))break;
   double p=2*(re[bin]*re[bin]+im[bin]*im[bin])/(N*ws);
   if(f>=kLo&&f<kHi)oneK+=p;
   double wp=p*Math.pow(10,weight(f,weighting)/10.0);
   if(f>=20){total+=wp;totalZ+=p;}
   for(int j=0;j<c.length;j++)if(f>=lo[j]&&f<hi[j])powers[j]+=wp;
  }
  double[] levels=new double[c.length];for(int j=0;j<c.length;j++)levels[j]=db(powers[j]);
  return new Result(names,c,levels,db(total),db(oneK),db(totalZ),clip);
 }

 /** DSP validation uses only deterministic digital PCM: no physical SPL inference. */
 public static String selfTest(){
  int[] frequencies={1000,500,5000};
  double[] amplitudes={0.5,0.1,0.8};
  StringBuilder result=new StringBuilder();
  boolean pass=true;
  for(int j=0;j<frequencies.length;j++){
   short[] samples=new short[N];
   double energy=0;
   for(int i=0;i<N;i++){
    samples[i]=(short)Math.round(amplitudes[j]*32767*Math.sin(2*Math.PI*frequencies[j]*i/48000.0));
    double x=samples[i]/32768.0;
    energy+=x*x;
   }
   double expected=20*Math.log10(Math.sqrt(energy/N));
   double actual=analyze(samples,48000,3,0).overall;
   double difference=Math.abs(actual-expected);
   boolean okay=difference<0.1;
   pass=pass&&okay;
   result.append(frequencies[j]).append(" Hz: expected ")
    .append(String.format(Locale.US,"%.2f",expected)).append(" dBFS; FFT ")
    .append(String.format(Locale.US,"%.2f",actual)).append(" dBFS; difference ")
    .append(String.format(Locale.US,"%.3f",difference))
    .append(" dB  ").append(okay?"PASS":"FAIL").append("\\n");
  }
  result.insert(0,pass?"FFT normalization: PASS\\n\\n":"FFT normalization: FAIL\\n\\n");
  return result.toString();
 }
 private static void fft(double[] re,double[] im){
  int n=re.length;
  for(int i=1,j=0;i<n;i++){
   int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;
   if(i<j){double x=re[i];re[i]=re[j];re[j]=x;x=im[i];im[i]=im[j];im[j]=x;}
  }
  for(int len=2;len<=n;len*=2){
   double angle=-2*Math.PI/len,w0r=Math.cos(angle),w0i=Math.sin(angle);
   for(int i=0;i<n;i+=len){
    double wr=1,wi=0;
    for(int j=0;j<len/2;j++){
     int a=i+j,b=a+len/2;double xr=re[b]*wr-im[b]*wi,xi=re[b]*wi+im[b]*wr;
     re[b]=re[a]-xr;im[b]=im[a]-xi;re[a]+=xr;im[a]+=xi;
     double next=wr*w0r-wi*w0i;wi=wr*w0i+wi*w0r;wr=next;
    }
   }
  }
 }
}
