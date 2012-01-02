import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

import utils.ErrorsReporting;
import utils.FileUtils;

import jsafran.DetGraph;
import jsafran.GraphIO;
import jsafran.JSafran;
import jsafran.Mot;

/**
 * prepare les data en entree du program HDB de Hal Daumé
 * 
 * @author xtof
 *
 */
public class PrepHDB {
	final static int nclassesMax=100;

	public static void main(String args[]) throws Exception {
		if (args[0].equals("-train")) {
			train(args[1],args[2],args[3]);
		} else if (args[0].equals("-test")) {
			test(args[1]);
		} else if (args[0].equals("-meanres")) {
			meanres();
		} else if (args[0].equals("-putclass")) {
			GraphIO gio = new GraphIO(null);
			List<DetGraph> gs = gio.loadAllGraphs(args[1]);
			saveGroups(gs, args[2], Integer.parseInt(args[3]),args[4]);
		}
	}

	public static void meanres() {
		try {
			BufferedReader f = new BufferedReader(new FileReader("verbsuj.classes"));
			HashMap<String, float[]> verb2classes = new HashMap<String, float[]>();
			int nclasses = 0;
			for (;;) {
				String s=f.readLine();
				if (s==null) break;
				String[] ss = s.split(" ");
				if (ss.length<3) continue;
				int classe = Integer.parseInt(ss[1]);
				if (classe>nclasses) nclasses = classe;
			}
			nclasses++;
			f.close();
			f = new BufferedReader(new FileReader("verbsuj.classes"));
			for (;;) {
				String s=f.readLine();
				if (s==null) break;
				String[] ss = s.split(" ");
				if (ss.length<3) continue;
				int classe = Integer.parseInt(ss[1]);
				for (int i=3;i<ss.length;i++) {
					int j=ss[i].indexOf('(');
					String v = ss[i].substring(0, j).trim();
					// PB: les classes peuvent etre interchangeables !!					
				}
			}
			f.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void saveGroups(java.util.List<DetGraph> gs, String logen, int trainOrTest, String en) {
		try {
			// lecture des indexes
			DataInputStream fin = new DataInputStream(new FileInputStream("indexes.hdb"));
			long idxTrain = fin.readLong();
			long idxTest  = fin.readLong();
			long idxEnd   = fin.readLong();
			fin.close();
			long idxdeb=idxTrain;
			long idxend=idxTest;
			if (trainOrTest==1) {
				idxdeb=idxTest;
				idxend=idxEnd;
			}
			
			// nb de classes ??
			int nclasses = 0, ninst=0;
			BufferedReader f = new BufferedReader(new FileReader(logen));
			for (int i=0;i<100;i++) {
				String s = f.readLine();
				if (s==null) break;
				if (s.startsWith("e = ")) {
					int p1=4;
					for (int j=0;j<idxdeb;j++) p1=s.indexOf(' ',p1)+1;
					ninst=0;
					for (int j=(int)idxdeb, k=0;j<idxend;j++,k++) {
						int l=s.indexOf(' ',p1);
						int ent=Integer.parseInt(s.substring(p1,l));
						ninst++;
						if (ent+1>nclasses) nclasses=ent+1;
						p1=l+1;
					}
				}
			}
			f.close();
			assert ninst==idxend-idxdeb;
			System.out.println("nclasses "+nclasses+" "+ninst);
			
			// lecture des classes samplees: pour chaque instance, on a un sample de E par iter. qui suit posterior P(E|sample)
			// on conserve la distribution empirique P(E|inst) = #(E=e)/#(E=*)
			int[][] counts = new int[ninst][nclasses];
			for (int i=0;i<ninst;i++) Arrays.fill(counts, 0);
			f = new BufferedReader(new FileReader(logen));
			for (;;) {
				String s = f.readLine();
				if (s==null) break;
				if (s.startsWith("e = ")) {
					int i=4;
					for (int j=0;j<idxdeb;j++) i=s.indexOf(' ',i)+1;
					int inst=0;
					for (int j=(int)idxdeb;j<idxend;j++) {
						int l=s.indexOf(' ',i);
						int classe=Integer.parseInt(s.substring(i,l));
						counts[inst][classe]++;
						inst++;
						i=l+1;
					}
				}
			}
			f.close();
			
			// pour chaque instance, on a P(E|inst); on calcule la valeur max de P(E|inst)
			int[] obs2classe = new int[(int)(idxend-idxdeb)];
			System.out.println("create obs2classe "+obs2classe.length);
			for (int j=(int)idxdeb, k=0;j<idxend;j++,k++) {
				int cmax=0;
				for (int l=1;l<counts[k].length;l++)
					if (counts[k][l]>counts[k][cmax]) cmax=l;
				obs2classe[k]=counts[k][cmax];
			}

			// lecture des obs
			PrintWriter fout = FileUtils.writeFileUTF("groups."+en+".tab");
			int widx=0;
			for (int i=0;i<gs.size();i++) {
				DetGraph g = gs.get(i);
				int nexinutt=0;
				for (int j=0;j<g.getNbMots();j++) {
					if (!isAnExemple(g, j)) continue;
					nexinutt++;
					
					// calcul du label
					String lab = "NO";
					int[] groups = g.getGroups(j);
					if (groups!=null)
						for (int gr : groups) {
							if (g.groupnoms.get(gr).equals(en)) {
								int debdugroupe = g.groups.get(gr).get(0).getIndexInUtt()-1;
								if (debdugroupe==j) lab = en+"B";
								else lab = en+"I";
								break;
							}
						}
					
					// calcul des features
					fout.println(g.getMot(j).getForme()+"\t"+g.getMot(j).getPOS()+"\t"+obs2classe[widx]+"\t"+lab);
					widx++;
				}
				if (nexinutt>0)
					fout.println();
			}
			fout.close();
			ErrorsReporting.report("groups saved in groups.*.tab");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void printClasseRepresentatives(int[][] nOcc, String[] vocinv) {
		// mot le plus frequent de chaque classe: on veut W* = argmax_W P(W|E)
		// W* = argmax_W P(E|W)P(W)
		// avec P(E|W) = #(w,e)/#(w,*)
		// et   P(W)   = #w/#*
		final int nbest = 6;
		int[][] bestmot4class = new int[nbest][nclassesMax];
		float[][] pwe = new float[nbest][nclassesMax];
		for (int i=0;i<nbest;i++) Arrays.fill(pwe[i], 0);
		int ntotw=0;
		for (int word=0;word<nOcc.length;word++) {
			for (int cl=0;cl<nOcc[word].length;cl++) {
				ntotw+=nOcc[word][cl];
			}			
		}
		for (int word=0;word<nOcc.length;word++) {
//			if (vocinv[word].charAt(0)=='@') continue;
			int nw=0;
			for (int cl=0;cl<nOcc[word].length;cl++) {
				nw += nOcc[word][cl];
			}
			float pw = (float)nw/(float)ntotw;
//			pw=1;
			for (int cl=0;cl<nOcc[word].length;cl++) {
				float pe_w = (float)nOcc[word][cl]/(float)nw;
				float pw_e = pe_w * pw;
				for (int i=0;i<nbest;i++) {
					if (pw_e>pwe[i][cl]) {
						// decale
						for (int j=nbest-1;j>i;j--) {
							pwe[j][cl]=pwe[j-1][cl];
							bestmot4class[j][cl]=bestmot4class[j-1][cl];
						}
						pwe[i][cl]=pw_e;
						bestmot4class[i][cl]=word;
						break;
					}
				}
			}
		}
		for (int cl=0;cl<nclassesMax;cl++) {
			if (pwe[0][cl]>0) {
				System.out.print("\t classe "+cl+" : ");
				for (int i=0;i<nbest;i++) {
					System.out.print(vocinv[bestmot4class[i][cl]]+" ");
//					System.out.print(vocinv[bestmot4class[i][cl]]+"("+pwe[i][cl]+") ");
				}
				System.out.println();
			}
		}
	}

	/**
	 * parse la sortie de HBC et recupere les samples de la variable E:
	 * 
	 * @param logfile
	 * @param nclasses
	 * @throws Exception
	 */
	public static void test(String logfile) throws Exception {

		HashMap<String, Integer> voc = new HashMap<String, Integer>();
		{
			// lecture des indices des verbes
			BufferedReader f = new BufferedReader(new FileReader("vocO"));
			for (;;) {
				String s = f.readLine();
				if (s==null) break;
				int i=s.lastIndexOf(' ');
				int idx = Integer.parseInt(s.substring(i+1));
				String verbe = s.substring(0,i);
				voc.put(verbe, idx);
			}
			f.close();
			System.out.println("verb voc read "+voc.size());
		}
		// pour afficher, on doit construire le vocabulaire inverse
		String[] vocinv = new String[voc.size()];
		for (String x : voc.keySet()) vocinv[voc.get(x)]=x;

		int[] wordAtInstance;
		{
			// lecture de quel verbe se trouve � l'instance t
			BufferedReader f = new BufferedReader(new FileReader("enO"));
			ArrayList<Integer> seq = new ArrayList<Integer>();
			for (;;) {
				String s = f.readLine();
				if (s==null) break;
				int idx = Integer.parseInt(s);
				seq.add(idx);
			}
			f.close();
			wordAtInstance = new int[seq.size()];
			for (int i=0;i<seq.size();i++) wordAtInstance[i]=seq.get(i)-1;
			System.out.println("words identified for ninstances = "+wordAtInstance.length);
		}

		int nclasses=0;
		// distribution empirique #(W,E)
		int[][] nOccs = new int[voc.size()][nclassesMax];
		{
			// lecture et cumul des classes par verbe
			for (int[] x : nOccs) Arrays.fill(x, 0);

			BufferedReader f = new BufferedReader(new FileReader(logfile));
			long ntot=0;
			for (int iter=0;;) {
				String s = f.readLine();
				if (s==null) break;
				if (s.startsWith("e = ")) {
					// nouvelle iteration
					// toutes les instances sont sur une ligne
					String[] ss = s.substring(4).split(" ");
					assert ss.length==wordAtInstance.length;
					for (int i=0;i<ss.length;i++) {
						int classe = Integer.parseInt(ss[i]);
						if (classe+1>nclasses) nclasses=classe+1;
						nOccs[wordAtInstance[i]][classe-1]++;
						ntot++;
					}
					if (++iter%10==0) {
						System.out.println("iter "+iter);
						printClasseRepresentatives(nOccs,vocinv);
					}
				}
			}
			f.close();
		}
	}

	private static boolean isAnExemple(DetGraph g, int w) {
		if (!g.getMot(w).getPOS().startsWith("N")) return false;
		return true;
	}
	
	static HashMap<String, Integer> vocV = new HashMap<String, Integer>();
	static HashMap<String, Integer> vocO = new HashMap<String, Integer>();
	static HashMap<Integer, Integer> noccV = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> noccO = new HashMap<Integer, Integer>();
	private static long saveObs(List<DetGraph> gs, PrintWriter fv, PrintWriter fo) {
		long nobs=0;
		for (DetGraph g :gs) {
			for (int i=0;i<g.getNbMots();i++) {
				if (!isAnExemple(g,i)) continue;
				String headword = "NO_HEAD";
				String govword = g.getMot(i).getLemme();
				int d = g.getDep(i);
				if (d>=0) {
					// j'accepte tous les HEADs, qqsoit le deplabel
					// mais seulement les HEADs qui sont des NOMS ou des VERBES
					Mot head = g.getMot(g.getHead(d));
					if (head.getPOS().startsWith("P")) {
						// preposition: on va chercher un head plus haut
						int h = g.getHead(d);
						d=g.getDep(h);
						if (d>=0) {
							head = g.getMot(g.getHead(d));
							if (head.getPOS().startsWith("N")) {
								// nom = OK
								headword = head.getLemme();
							} else if (head.getPOS().startsWith("V")) {
								// verb = OK
								headword = head.getLemme();
							}
						}
					} else if (head.getPOS().startsWith("N")) {
						// nom = OK
						headword = head.getLemme();
					} else if (head.getPOS().startsWith("V")) {
						// verb = OK
						headword = head.getLemme();
					}
				}

				Integer oi = vocO.get(govword);
				if (oi==null) {
					oi=vocO.size();
					vocO.put(govword,oi);
				}
				Integer vi = vocV.get(headword);
				if (vi==null) {
					vi=vocV.size();
					vocV.put(headword,vi);
				}
				fo.println((oi+1));
				fv.println((vi+1));
				nobs++;
						
				{
					Integer noc = noccV.get(vi);
					if (noc==null) noc=1;
					else noc++;
					noccV.put(vi, noc);
				}
				{
					Integer noc = noccO.get(oi);
					if (noc==null) noc=1;
					else noc++;
					noccO.put(oi, noc);
				}
			}
		}
		return nobs;
	}
	
	// cree le fichier d'exemples qui passera dans le programme en.hier -> en.c
	// 1 exemple = tous les mots de type N*
	public static void train(String unlabeled, String train, String test) throws Exception {
		//		final static String corp = "../../git/jsafran/train2011.xml";
		//		final static String corp = "../../git/jsafran/c0b.conll";

		PrintWriter fv = new PrintWriter(new FileWriter("enV.0"));
		PrintWriter fo = new PrintWriter(new FileWriter("enO.0"));
		GraphIO gio = new GraphIO(null);
		List<DetGraph> gs = gio.loadAllGraphs(unlabeled);
		long idxTrain = saveObs(gs, fv, fo);
		gs = gio.loadAllGraphs(train);
		long idxTest  = idxTrain+saveObs(gs, fv, fo);
		gs = gio.loadAllGraphs(test);
		long idxEnd   = idxTest+saveObs(gs, fv, fo);
		fv.close();
		fo.close();
		
		System.out.println("debugidx "+idxTrain+" "+idxTest+" "+idxEnd);
		
		saveVoc(vocV,"vocV");
		saveVoc(vocO,"vocO");

		// supprime les obs trop peu frequentes
		final int MINOCC = 1000;

		PrintWriter f = new PrintWriter(new FileWriter("enV"));
		PrintWriter g = new PrintWriter(new FileWriter("enO"));
		BufferedReader f0 = new BufferedReader(new FileReader("enV.0"));
		BufferedReader g0 = new BufferedReader(new FileReader("enO.0"));
		long nUnlabDel = 0;
		long nTrainDel = 0;
		long nTestDel = 0;
		for (int idx=0;;idx++) {
			String s = f0.readLine();
			if (s==null) break;
			int v = Integer.parseInt(s);
			s = g0.readLine();
			int o = Integer.parseInt(s);
			Integer noco = noccO.get(o-1);
			Integer nocv = noccV.get(v-1);
			if (nocv>MINOCC&&noco>MINOCC) {
				f.println(v);
				g.println(o);
			} else {
				// supprime une obs
				if (idx<idxTrain) nUnlabDel++;
				else if (idx<idxTest) nTrainDel++;
				else nTestDel++;
			}
		}
		g0.close();
		f0.close();
		f.close();
		g.close();
		
		// sauve les index
		idxTrain-=nUnlabDel;
		idxTest-=nUnlabDel+nTrainDel;
		DataOutputStream ff = new DataOutputStream(new FileOutputStream("indexes.hdb"));
		ff.writeLong(idxTrain);
		ff.writeLong(idxTest);
		ff.writeLong(idxEnd);
		System.out.println("indexes: "+idxTrain+" "+idxTest+" "+idxEnd);
		ff.close();
	}

	private static void saveVoc(Map<String, Integer> voc, String fn) {
		try {
			PrintWriter f = new PrintWriter(new FileWriter(fn));
			for (String s : voc.keySet()) {
				f.println(s+" "+voc.get(s));
			}
			f.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
