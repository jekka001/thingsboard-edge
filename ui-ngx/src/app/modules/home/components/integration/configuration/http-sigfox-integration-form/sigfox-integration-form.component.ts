import { Component, forwardRef } from '@angular/core';
import { FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR } from '@angular/forms';
import { HttpIntegrationFormComponent } from './http-integration-form.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { IntegrationType } from '@shared/models/integration.models';

@Component({
  selector: 'tb-sigfox-integration-form',
  templateUrl: './base-http-integration-form.component.html',
  styleUrls: ['./base-http-integration-form.component.scss'],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SigfoxIntegrationFormComponent),
    multi: true
  },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => SigfoxIntegrationFormComponent),
      multi: true,
    }]
})
export class SigfoxIntegrationFormComponent extends HttpIntegrationFormComponent {

  protected integrationType = IntegrationType.SIGFOX;

  constructor(protected fb: FormBuilder,
              protected store: Store<AppState>,
              protected translate: TranslateService) {
    super(fb, store, translate);
  }

}
