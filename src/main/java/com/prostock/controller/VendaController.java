package com.prostock.controller;

import com.prostock.dto.VendaRequest;
import com.prostock.dto.VendaResponse;
import com.prostock.dto.VendaResumoResponse;
import com.prostock.model.Venda;
import com.prostock.model.enums.FormaPagamento;
import com.prostock.security.UsuarioPrincipal;
import com.prostock.service.PixService;
import com.prostock.service.VendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendaController {

    private final VendaService vendaService;
    private final PixService pixService;

    @GetMapping
    public List<VendaResumoResponse> listar() {
        return vendaService.listarTodas().stream().map(VendaResumoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendaResponse finalizar(@Valid @RequestBody VendaRequest request,
                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        Venda venda = vendaService.finalizar(request, principal.getUsuario());
        return montarResposta(venda);
    }

    @GetMapping("/{id}")
    public VendaResponse buscar(@PathVariable Long id) {
        return montarResposta(vendaService.buscarPorId(id));
    }

    @PatchMapping("/{id}/cancelar")
    public VendaResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        Venda venda = vendaService.cancelar(id, principal.getUsuario());
        return montarResposta(venda);
    }

    private VendaResponse montarResposta(Venda venda) {
        String pix = venda.getFormaPagamento() == FormaPagamento.PIX
                ? pixService.gerarCopiaECola(venda.getValorTotal(), "VDA" + venda.getId())
                : null;
        return VendaResponse.from(venda, pix);
    }
}
