import { map, OperatorFunction, pipe } from 'rxjs';

import { ConfigState, FeatureName, FeaturesConfig } from '@core/config/config.state';

export const selectIsFeatureEnabled = (feature: FeatureName): OperatorFunction<ConfigState, boolean> =>
  pipe(map((state) => state.features[feature]));

export const selectFeatures: OperatorFunction<ConfigState, FeaturesConfig> = pipe(map((state) => state.features));
