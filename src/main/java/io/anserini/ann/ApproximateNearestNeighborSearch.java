/*
 * Anserini: A Lucene toolkit for replicable information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.ann;

import io.anserini.analysis.AnalyzerUtils;
import io.anserini.ann.fw.FakeWordsEncoderAnalyzer;
import io.anserini.ann.lexlsh.LexicalLshAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.ParserProperties;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

public class ApproximateNearestNeighborSearch {
  private static final String FW = "fw";
  private static final String LEXLSH = "lexlsh";

  public static final class Args {
    @Option(name = "-input", metaVar = "[file]", usage = "vectors model")
    public File input;

    @Option(name = "-path", metaVar = "[path]", required = true, usage = "index path")
    public Path path;

    @Option(name = "-word", metaVar = "[word]", required = true, usage = "input word")
    public String word;

    @Option(name="-stored", metaVar = "[boolean]", usage = "fetch stored vectors from index")
    public boolean stored;

    @Option(name = "-encoding", metaVar = "[word]", required = true, usage = "encoding must be one of {fw, lexlsh}")
    public String encoding;

    @Option(name = "-depth", metaVar = "[int]", usage = "retrieval depth")
    public int depth = 10;

    @Option(name = "-lexlsh.n", metaVar = "[int]", usage = "n-grams")
    public int ngrams = 2;

    @Option(name = "-lexlsh.d", metaVar = "[int]", usage = "decimals")
    public int decimals = 1;

    @Option(name = "-lexlsh.hsize", metaVar = "[int]", usage = "hash set size")
    public int hashSetSize = 1;

    @Option(name = "-lexlsh.h", metaVar = "[int]", usage = "hash count")
    public int hashCount = 1;

    @Option(name = "-lexlsh.b", metaVar = "[int]", usage = "bucket count")
    public int bucketCount = 300;

    @Option(name = "-fw.q", metaVar = "[int]", usage = "quantization factor")
    public int q = 60;

    @Option(name = "-cutoff", metaVar = "[float]", usage = "tf cutoff factor")
    public float cutoff = 0.999f;

    @Option(name = "-msm", metaVar = "[float]", usage = "minimum should match")
    public float msm = 0f;
  }

  public static void main(String[] args) throws Exception {
    ApproximateNearestNeighborSearch.Args indexArgs = new ApproximateNearestNeighborSearch.Args();
    CmdLineParser parser = new CmdLineParser(indexArgs, ParserProperties.defaults().withUsageWidth(90));

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.err.println("Example: " + ApproximateNearestNeighborSearch.class.getSimpleName() +
          parser.printExample(OptionHandlerFilter.REQUIRED));
      return;
    }
    Analyzer vectorAnalyzer;
    if (indexArgs.encoding.equalsIgnoreCase(FW)) {
      vectorAnalyzer = new FakeWordsEncoderAnalyzer(indexArgs.q);
    } else if (indexArgs.encoding.equalsIgnoreCase(LEXLSH)) {
      vectorAnalyzer = new LexicalLshAnalyzer(indexArgs.decimals, indexArgs.ngrams, indexArgs.hashCount,
          indexArgs.bucketCount, indexArgs.hashSetSize);
    } else {
      parser.printUsage(System.err);
      System.err.println("Example: " + ApproximateNearestNeighborSearch.class.getSimpleName() +
          parser.printExample(OptionHandlerFilter.REQUIRED));
      return;
    }

    if (!indexArgs.stored && indexArgs.input == null) {
      System.err.println("Either -path or -stored args must be set");
      return;
    }

    Path indexDir = indexArgs.path;
    if (!Files.exists(indexDir)) {
      Files.createDirectories(indexDir);
    }

    System.out.println(String.format("Reading index at %s", indexArgs.path));

    Directory d = FSDirectory.open(indexDir);
    DirectoryReader reader = DirectoryReader.open(d);
    IndexSearcher searcher = new IndexSearcher(reader);
    if (indexArgs.encoding.equalsIgnoreCase(FW)) {
      searcher.setSimilarity(new ClassicSimilarity());
    }

    Collection<String> vectorStrings = new LinkedList<>();
//    if (indexArgs.stored) {
//      TopDocs topDocs = searcher.search(new TermQuery(new Term(IndexVectors.FIELD_ID, indexArgs.word)), indexArgs.depth);
//      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
//        vectorStrings.add(reader.document(scoreDoc.doc).get(IndexVectors.FIELD_VECTOR));
//      }
//    } else {
//      System.out.println(String.format("Loading model %s", indexArgs.input));
//
//      Map<String, List<float[]>> wordVectors = IndexVectors.readGloVe(indexArgs.input);
//
//      if (wordVectors.containsKey(indexArgs.word)) {
//        List<float[]> vectors = wordVectors.get(indexArgs.word);
//        for (float[] vector : vectors) {
//          StringBuilder sb = new StringBuilder();
//          for (double fv : vector) {
//            if (sb.length() > 0) {
//              sb.append(' ');
//            }
//            sb.append(fv);
//          }
//          String vectorString = sb.toString();
//          vectorStrings.add(vectorString);
//        }
//      }
//    }
    vectorStrings.add("-0.029403749853372574 -0.0027747477870434523 0.00902639515697956 -0.10073486715555191 -0.029882008209824562 -0.08363604545593262 -0.02160770818591118 0.006238104309886694 0.08928768336772919 -0.11966487765312195 -0.0298086479306221 -0.027881504967808723 0.026845982298254967 -0.07932324707508087 0.038269173353910446 0.03355148062109947 -0.07320605218410492 -0.034589823335409164 0.0049720583483576775 0.01366113219410181 0.03512592613697052 0.10055992752313614 0.005400374997407198 -0.014833078719675541 -0.0674218013882637 -0.055747490376234055 -0.03836510702967644 -0.06267871707677841 0.008624178357422352 -0.032702185213565826 -0.05064888671040535 -0.02573145553469658 0.005009303335100412 -0.01237533614039421 -0.15259137749671936 -0.05998833477497101 4.548397264443338E-4 -0.06488378345966339 -0.006131588947027922 -0.05506466329097748 0.0732201635837555 0.029822755604982376 -0.03601331636309624 0.1665441244840622 -0.026862911880016327 -0.017149601131677628 0.004822936840355396 -0.008791498839855194 0.020345047116279602 -0.07528838515281677 0.06690686196088791 -0.06307656317949295 0.08189231157302856 0.06147954240441322 0.01863657683134079 -0.013502982445061207 -0.05245611071586609 -0.0019521145150065422 0.029063748195767403 -0.014247599057853222 0.015074323862791061 -0.0475761741399765 0.015498973429203033 0.049089957028627396 -0.014085217379033566 0.052117519080638885 -0.07465493679046631 0.017503710463643074 -0.06507565081119537 -0.05429155007004738 -0.01426876150071621 -0.02487792633473873 0.05300913751125336 0.023104559630155563 -0.031009232625365257 -0.03786709904670715 0.11950264126062393 -0.05025104060769081 -0.011849532835185528 -0.02860524132847786 -0.0797690600156784 0.02696307748556137 -0.019940150901675224 -0.11021115630865097 0.09760995209217072 -0.011798180639743805 -0.07659619301557541 0.023189205676317215 0.005305428523570299 -0.0971980020403862 -0.09693700820207596 -0.01885807141661644 -0.0674218013882637 0.028392212465405464 0.012008952908217907 -0.009010029956698418 -0.02413020469248295 -0.04575483873486519 -0.02486240677535534 -0.07251476496458054 -0.07094737142324448 0.03273604437708855 -0.015975821763277054 -0.15010838210582733 -0.004988423082977533 -0.07149899750947952 -0.038257889449596405 -0.023448791354894638 -0.0889025330543518 0.007653834763914347 -0.00679691880941391 0.04131084308028221 -0.004326337948441505 -0.03476899862289429 -0.03820991888642311 -0.060047589242458344 -0.05526217818260193 0.02599809505045414 -0.0025072614662349224 -0.04984896257519722 -0.06923467665910721 -0.12807461619377136 0.019570522010326385 -0.10795529931783676 -0.06534511595964432 -0.04532031714916229 -0.012164986692368984 0.1473996639251709 -0.05631745234131813 0.0980190858244896 -0.014639800414443016 0.12233692407608032 0.032084256410598755 0.06184916943311691 0.012099949643015862 -0.032230980694293976 0.060791075229644775 0.009055457077920437 -0.003939780872315168 -0.013128276914358139 0.09196678549051285 0.08343853801488876 -0.04762837290763855 -0.053232043981552124 7.366033387370408E-4 0.1579100787639618 -0.033640362322330475 -0.022613603621721268 0.060490578413009644 -0.022894348949193954 -0.017214497551321983 -0.014968515373766422 0.00222355080768466 0.0032088488806039095 -0.025019004940986633 -0.012938524596393108 -0.04113590344786644 0.026853036135435104 -0.04961477220058441 0.0388856902718544 -0.029029889032244682 0.016184618696570396 -0.04814472422003746 -9.299242519773543E-4 0.021015172824263573 -0.0037755644880235195 2.733129367697984E-4 0.07516564428806305 -0.10734442621469498 0.008899987675249577 -0.10170266032218933 -0.0058237542398273945 -0.13566750288009644 0.002930076327174902 0.022746216505765915 -0.04844945669174194 0.09835062175989151 -0.022598084062337875 -0.016507688909769058 -0.009909269399940968 -0.043415747582912445 0.05606633052229881 0.056423261761665344 -0.0956517681479454 0.08138018846511841 -0.06785773485898972 0.08368401229381561 -0.05962293967604637 0.04036702588200569 -0.03696701303124428 0.007438688538968563 0.08698809891939163 -0.051918599754571915 -0.04010743647813797 -0.056507907807826996 -0.04240138828754425 -0.038717806339263916 -0.006451415829360485 -0.07915254682302475 0.03410733491182327 0.12221841514110565 -0.11810453981161118 0.019133176654577255 0.036957137286663055 -0.06074170023202896 0.048754189163446426 0.008385895751416683 0.08725050091743469 0.01669955812394619 -0.0027042082510888577 0.06729059666395187 -0.045801397413015366 -0.021815095096826553 -0.03323264420032501 -0.09066179394721985 -0.013001305051147938 -0.027682581916451454 0.05737131088972092 0.02540697157382965 0.013305049389600754 0.0066190180368721485 0.037201203405857086 -0.07156530022621155 0.0528920441865921 -0.09420288354158401 0.049511782824993134 -0.004773418884724379 0.04307715594768524 0.03268243372440338 0.0033190317917615175 -0.0964488759636879 0.03679066523909569 -0.03177952766418457 -0.03747066482901573 0.084601029753685 0.03665240481495857 0.05113843083381653 0.021957585588097572 -0.06426021456718445 0.01573457568883896 -0.04696107283234596 0.011478777043521404 -0.05218382552266121 -0.036035891622304916 -0.1640470176935196 -0.020628616213798523 -0.0046515255235135555 -0.07846690714359283 0.06731881201267242 -0.04094262793660164 0.060223937034606934 0.1748960167169571 -0.11482585966587067 0.029745161533355713 -0.03587082773447037 -0.012251326814293861 -0.011062310077250004 0.036729998886585236 0.04628812149167061 -0.033544428646564484 0.0072486549615859985 -0.004267225973308086 -0.022105718031525612 0.008062259294092655 0.047828711569309235 0.01805109716951847 -0.03028690628707409 -0.10610292851924896 0.05843787267804146 8.848353754729033E-4 -0.07463659346103668 0.13006524741649628 -0.05950584262609482 -0.0982448160648346 0.010456093586981297 0.026905234903097153 -0.16973251104354858 -0.01147440355271101 -0.06932637095451355 -0.03126176446676254 -0.042148854583501816 0.042456407099962234 0.002628871938213706 0.02650315873324871 -0.06409092247486115 -0.041330594569444656 0.052128806710243225 -0.03416658937931061 -0.016651591286063194 0.010125966742634773 0.06211157515645027 -0.08461654931306839 0.06398510932922363 0.025188300758600235 -0.02420215681195259 0.0026538430247455835 -0.08796858787536621 -0.001998106250539422 0.023699913173913956 -0.00908437930047512");


    for (String vectorString : vectorStrings) {
      float msm = indexArgs.msm;
      float cutoff = indexArgs.cutoff;
      CommonTermsQuery simQuery = new CommonTermsQuery(SHOULD, SHOULD, cutoff);
      for (String token : AnalyzerUtils.analyze(vectorAnalyzer, vectorString)) {
        simQuery.add(new Term(IndexVectors.FIELD_VECTOR, token));
      }
      if (msm > 0) {
        simQuery.setHighFreqMinimumNumberShouldMatch(msm);
        simQuery.setLowFreqMinimumNumberShouldMatch(msm);
      }

      long start = System.currentTimeMillis();
      TopScoreDocCollector results = TopScoreDocCollector.create(indexArgs.depth);
      searcher.search(simQuery, results);
      long time = System.currentTimeMillis() - start;

      System.out.println(String.format("%d nearest neighbors of '%s':", indexArgs.depth, indexArgs.word));

      int rank = 1;
      for (ScoreDoc sd : results.topDocs().scoreDocs) {
        Document document = reader.document(sd.doc);
        String word = document.get(IndexVectors.FIELD_ID);
        System.out.println(String.format("%d. %s (%.3f)", rank, word, sd.score));
        rank++;
      }
      System.out.println(String.format("Search time: %dms", time));
    }
    reader.close();
    d.close();
  }
}
