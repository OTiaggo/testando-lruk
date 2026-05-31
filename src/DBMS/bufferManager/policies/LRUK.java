package DBMS.bufferManager.policies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import DBMS.bufferManager.IPage;

public class LRUK extends AbstractBufferPolicy {

    // Classe interna para armazenar as faixas de tempo T1 e T2 de cada página
    private class PageHistory {
        IPage page;
        long t1;
        long t2;

        public PageHistory(IPage page, long t1, long t2) {
            this.page = page;
            this.t1 = t1;
            this.t2 = t2;
        }
    }

    // Mapa para busca e armazenamento do estado histórico de cada página
    private Map<String, PageHistory> buffer;

    public LRUK(Integer capacity) {
        super(capacity);
        this.buffer = new HashMap<>();
    }

    @Override
    public synchronized IPage find(String pageId) {
        // O numberOfOperation atua como o Contador Global (GC) citado na teoria
        super.numberOfOperation++; 
        long gc = super.numberOfOperation;

        if (buffer.containsKey(pageId)) {
            // HIT: A página foi encontrada no buffer
            PageHistory ph = buffer.get(pageId);
            
            action(() -> {
                super.hitCount++;
                ph.page.addHitCount();
                
                // Deslocamento das faixas conforme o algoritmo LRU-K (K=2)
                ph.t2 = ph.t1;
                ph.t1 = gc;
                
                if(policyListener != null) policyListener.hit(ph.page);
            });
            
            return ph.page;
        }

        // MISS: A página não foi encontrada
        super.missCount++;
        return null;
    }

    @Override
    public void insert(IPage p) {
        action(() -> {
            // Se o buffer estiver cheio, escolhe uma vítima
            while (buffer.size() >= super.capacity) {
                replacement();
            }
            alloc(p); // Aloca espaço real na memória
            
            // Conforme a teoria: novas páginas recebem T1 = GC e T2 = 0
            long gc = super.numberOfOperation;
            buffer.put(p.getPageId(), new PageHistory(p, gc, 0));
            
            if(policyListener != null) policyListener.insert(p);
        });
    }

    public void replacement() {
        action(() -> {
            PageHistory victim = null;
            
            // Busca a página com o menor valor na faixa K (Neste caso, T2).
            for (PageHistory ph : buffer.values()) {
                if (victim == null) {
                    victim = ph;
                } else {
                    // Seleciona o menor T2
                    if (ph.t2 < victim.t2) {
                        victim = ph;
                    } 
                    // Critério de desempate: se ambas tiverem o mesmo T2 (ex: T2 = 0), 
                    // seleciona a que tiver o menor T1 (a referência mais antiga).
                    else if (ph.t2 == victim.t2 && ph.t1 < victim.t1) {
                        victim = ph;
                    }
                }
            }

            if (victim != null) {
                remove(victim.page);
            }
        });
    }

    @Override
    public void remove(IPage p) {
        action(() -> {
            buffer.remove(p.getPageId());
            free(p); // Libera da memória do Seal-DB
            if(policyListener != null) policyListener.remove(p);
            if(policyListener != null) policyListener.setLastRemoved(p);
        });
    }

    @Override
    public String getName() {
        return "LRU-K (K=2)";
    }

    @Override
    public List<IPage> getPages() {
        // Retorna a lista de páginas para a interface gráfica ser atualizada
        List<IPage> allPages = new ArrayList<>();
        for (PageHistory ph : buffer.values()) {
            allPages.add(ph.page);
        }
        return allPages;
    }

    @Override
    public int getCurrentNumberOfPages() {
        return buffer.size();
    }

    @Override
    public void setPolicyListener(BufferPolicyListener listener) {
        this.policyListener = listener;
    }

    @Override
    protected void logicRemoveAll() {
        action(() -> {
            buffer.clear();
        });
    }
}